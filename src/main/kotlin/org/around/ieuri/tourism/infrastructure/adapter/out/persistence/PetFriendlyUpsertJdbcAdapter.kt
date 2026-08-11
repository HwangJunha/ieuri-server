package org.around.ieuri.tourism.infrastructure.adapter.out.persistence

import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand
import org.around.ieuri.tourism.application.port.out.persistence.PetFriendlyStorePort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.PreparedStatement

/**
 * 반려동물 동반 가능 관광지 upsert 어댑터(SPEC-0015 · V48). JPA에 이식 가능한 upsert가 없어
 * 이 어댑터에만 생 SQL이 남는다([SpotUpsertJdbcAdapter]와 같은 처리).
 *
 * 한 페이지를 한 번의 `executeBatch`로 보낸다.
 */
@Component
class PetFriendlyUpsertJdbcAdapter(
    private val jdbcTemplate: JdbcTemplate,
) : PetFriendlyStorePort {

    @Transactional
    override fun upsertAll(records: List<SyncPetFriendlyCommand>): Int {
        if (records.isEmpty()) return 0
        val setter = ParameterizedPreparedStatementSetter<SyncPetFriendlyCommand> { ps: PreparedStatement, it ->
            ps.setString(1, it.contentId)
            // setString(null)은 NULL을 넣는다 — 빈 문자열로 바뀌지 않는다(POL-043).
            ps.setString(2, it.accompanyType)
            ps.setString(3, it.accompanyScope)
            ps.setString(4, it.requiredMatters)
            ps.setString(5, it.etcInfo)
        }
        // batchUpdate(sql, batchArgs, batchSize, pss)는 청크별 결과를 배열의 배열로 돌려준다.
        return jdbcTemplate.batchUpdate(UPSERT_SQL, records, records.size, setter)
            .sumOf { chunk -> chunk.count { it != 0 } }
    }

    /** SQL의 `?` 개수와 setter의 인자 수가 어긋나면 실행 시점에야 드러나므로 테스트가 여기 접근한다. */
    internal companion object {
        /**
         * `updated_at`은 값이 실제로 바뀐 경우에만 올린다. 무조건 갱신하면 매 실행마다 전 행이
         * 변경된 것처럼 보여 "언제 값이 바뀌었나"를 알 수 없다.
         *
         * **spot에 FK를 걸지 않는다**(V48 주석) — 반려동물 목록에만 있고 아직 적재하지 않은
         * content_id가 와도 이 문장이 실패하면 안 된다.
         */
        val UPSERT_SQL = """
            INSERT INTO spot_pet_friendly (
                content_id, accompany_type, accompany_scope, required_matters, etc_info
            ) VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (content_id) DO UPDATE SET
                accompany_type   = EXCLUDED.accompany_type,
                accompany_scope  = EXCLUDED.accompany_scope,
                required_matters = EXCLUDED.required_matters,
                etc_info         = EXCLUDED.etc_info,
                updated_at       = CASE
                    WHEN spot_pet_friendly.accompany_type   IS DISTINCT FROM EXCLUDED.accompany_type
                      OR spot_pet_friendly.accompany_scope  IS DISTINCT FROM EXCLUDED.accompany_scope
                      OR spot_pet_friendly.required_matters IS DISTINCT FROM EXCLUDED.required_matters
                      OR spot_pet_friendly.etc_info         IS DISTINCT FROM EXCLUDED.etc_info
                    THEN now()
                    ELSE spot_pet_friendly.updated_at
                END
        """.trimIndent()
    }
}
