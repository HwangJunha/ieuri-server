package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.application.port.out.persistence.SpotStorePort
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.springframework.jdbc.core.BatchPreparedStatementSetter
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant

/**
 * 관광지 목록 적재 포트 구현(SPEC-0004). Command ↔ 테이블 매핑을 여기서 담당한다
 * (별도 Mapper 클래스 없음, 프로젝트 관례).
 *
 * **네이티브 `INSERT ... ON CONFLICT` + JDBC 배치를 쓴다.** JPA에는 이식 가능한 upsert가 없고, 대안인
 * "자연키로 조회 후 있으면 dirty-checking / 없으면 persist"(spot_reaction 관례)는 배치의 페이지당
 * 수백~천 건 규모에서 건당 SELECT를 발생시켜 맞지 않는다. 단건 재평가와 대량 동기화는 쓰기 성격이
 * 다르므로 관례를 그대로 옮기지 않는다. 한 페이지를 한 번의 `executeBatch`로 보내 왕복 횟수를 줄인다.
 *
 * 충돌 시 **전체 컬럼을 무조건 덮어쓴다**(설계 문서 §2.4) — 해시 비교로 변경을 감지하면 낡은 저작권
 * 분류가 무기한 남는 위험이 있기 때문이다(런타임 계약 위험 5번).
 */
@Component
class SpotJpaAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : SpotStorePort {

    @Transactional
    override fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int {
        if (spots.isEmpty()) return 0
        val syncedTimestamp = Timestamp.from(syncedAt)
        val setter = ParameterizedPreparedStatementSetter<SyncSpotCommand> { ps: PreparedStatement, spot ->
            ps.setString(1, spot.contentId)
            ps.setString(2, spot.language.name)
            ps.setString(3, spot.title)
            ps.setString(4, spot.address)
            ps.setString(5, spot.thumbnailImageUrl)
            ps.setDouble(6, spot.coordinates.longitude)
            ps.setDouble(7, spot.coordinates.latitude)
            ps.setString(8, spot.category.name)
            spot.contentTypeId?.let { ps.setInt(9, it) } ?: ps.setNull(9, Types.INTEGER)
            ps.setString(10, spot.copyright.name)
            ps.setTimestamp(11, syncedTimestamp)
        }
        // batchUpdate(sql, batchArgs, batchSize, pss)는 청크별 결과를 배열의 배열로 돌려준다.
        return jdbcTemplate.batchUpdate(UPSERT_SQL, spots, spots.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    /**
     * 생존 집합을 **임시 테이블에 넣고 anti-join**으로 지운다. `NOT IN (...)`에 6만 개 파라미터를
     * 넣으면 파서·플래너가 감당하지 못하고 드라이버 한도(65535)도 넘는다.
     *
     * 임시 테이블은 `ON COMMIT DROP`이라 트랜잭션이 끝나면 사라진다 — 동시 실행이나 실패 시
     * 찌꺼기가 남지 않는다. 배치는 언어별 순차 실행이라 경합도 없다.
     */
    @Transactional
    override fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int {
        // 빈 집합으로 호출되면 전량 삭제가 된다. 게이트가 막아야 하지만, 여기서도 한 번 더 막는다 —
        // 이 한 줄이 없으면 게이트 버그가 곧 데이터 전량 손실이다(AC-14).
        require(survivingContentIds.isNotEmpty()) { "생존 집합이 비어 있으면 정리하지 않는다." }

        jdbcTemplate.execute(
            "CREATE TEMP TABLE surviving_spot (content_id VARCHAR(32) PRIMARY KEY) ON COMMIT DROP",
        )
        val ids = survivingContentIds.toList()
        jdbcTemplate.batchUpdate(
            "INSERT INTO surviving_spot (content_id) VALUES (?) ON CONFLICT DO NOTHING",
            object : BatchPreparedStatementSetter {
                override fun getBatchSize() = ids.size
                override fun setValues(ps: PreparedStatement, i: Int) = ps.setString(1, ids[i])
            },
        )
        return jdbcTemplate.update(
            """
            DELETE FROM spot s
            WHERE s.language = ?
              AND NOT EXISTS (SELECT 1 FROM surviving_spot t WHERE t.content_id = s.content_id)
            """.trimIndent(),
            language.name,
        )
    }

    @Transactional(readOnly = true)
    override fun countByLanguage(language: DomainLocale): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spot WHERE language = ?",
            Long::class.java,
            language.name,
        ) ?: 0L

    private companion object {
        /**
         * `updated_at`은 **실제로 값이 바뀐 경우에만** 올린다(CASE 절). 무조건 갱신하면 매일 전 행이
         * 변경된 것처럼 보여 "실제로 바뀐 것"을 구분할 수 없어진다. `last_synced_at`은 반대로 항상
         * 갱신해야 정리 단계(R-5)가 "이번 실행이 이 행을 봤다"를 판정할 수 있다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot (
                content_id, language, title, address, thumbnail_image_url,
                map_x, map_y, category, content_type_id, copyright, last_synced_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (content_id, language) DO UPDATE SET
                title               = EXCLUDED.title,
                address             = EXCLUDED.address,
                thumbnail_image_url = EXCLUDED.thumbnail_image_url,
                map_x               = EXCLUDED.map_x,
                map_y               = EXCLUDED.map_y,
                category            = EXCLUDED.category,
                content_type_id     = EXCLUDED.content_type_id,
                copyright           = EXCLUDED.copyright,
                last_synced_at      = EXCLUDED.last_synced_at,
                updated_at          = CASE
                    WHEN spot.title               IS DISTINCT FROM EXCLUDED.title
                      OR spot.address             IS DISTINCT FROM EXCLUDED.address
                      OR spot.thumbnail_image_url IS DISTINCT FROM EXCLUDED.thumbnail_image_url
                      OR spot.map_x               IS DISTINCT FROM EXCLUDED.map_x
                      OR spot.map_y               IS DISTINCT FROM EXCLUDED.map_y
                      OR spot.category            IS DISTINCT FROM EXCLUDED.category
                      OR spot.content_type_id     IS DISTINCT FROM EXCLUDED.content_type_id
                      OR spot.copyright           IS DISTINCT FROM EXCLUDED.copyright
                    THEN now()
                    ELSE spot.updated_at
                END
        """.trimIndent()
    }
}
