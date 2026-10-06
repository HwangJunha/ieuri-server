package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotCatalogTranslationTable
import org.around.ieuri.tourism.adapter.persistence.table.SpotTable
import org.around.ieuri.tourism.application.catalog.required.CatalogLanguage
import org.around.ieuri.tourism.application.catalog.required.SpotCatalogRepository
import org.around.ieuri.tourism.application.catalog.required.VisibilityRefreshResult
import org.around.ieuri.tourism.domain.catalog.SpotCatalog
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.exists
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.not
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.insertReturning
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * 카탈로그 저장소 구현(SPEC-0019·SPEC-0029).
 * - 번역 upsert와 축제 기간 확정은 raw SQL이다 — 조건부 갱신(`IS DISTINCT FROM`)과 `DISTINCT ON`을 DSL이 표현하지 못한다.
 */
@Repository
class SpotCatalogPersistenceAdapter(
    private val exposedJdbcTemplate: JdbcTemplate,
) : SpotCatalogRepository {

    /** 카탈로그를 먼저 넣어 id를 받은 뒤 번역을 붙인다(FK 순서). */
    override fun save(catalog: SpotCatalog): Long {
        val catalogId = SpotCatalogTable.insertReturning(listOf(SpotCatalogTable.id)) { catalog.standard.writeTo(it) }
            .single()[SpotCatalogTable.id]

        SpotCatalogTranslationTable.insert { catalog.translation.writeTo(it, catalogId, catalog.language, catalog.standard) }
        return catalogId
    }

    override fun assign(spotId: Long, catalogId: Long) {
        SpotTable.update({ (SpotTable.id eq spotId) and SpotTable.catalogId.isNull() }) { it[SpotTable.catalogId] = catalogId }
    }

    override fun reassign(spotIds: List<Long>, catalogId: Long) {
        if (spotIds.isEmpty()) return
        SpotTable.update({ SpotTable.id inList spotIds }) { it[SpotTable.catalogId] = catalogId }
    }

    /** `catalog_id`가 두 번(값 자리·SELECT의 WHERE) 들어간다. */
    override fun upsertTranslation(catalogId: Long, language: DomainLocale, title: String, address: String?) {
        exposedJdbcTemplate.update(UPSERT_TRANSLATION_SQL, catalogId, language.name, title, address, catalogId)
    }

    /** 빈 목록은 무동작이다 — 조건 없는 delete가 되면 번역이 전부 사라진다. */
    override fun deleteTranslations(catalogId: Long, languages: List<DomainLocale>) {
        if (languages.isEmpty()) return
        SpotCatalogTranslationTable.deleteWhere {
            (SpotCatalogTranslationTable.catalogId eq catalogId) and (SpotCatalogTranslationTable.language inList languages.map { it.name })
        }
    }

    /** 두 문장은 한 트랜잭션이어야 하고 순서가 있다 — 삭제가 먼저면 방금 쓴 기간을 지운다. */
    override fun refreshFestivalPeriod(catalogId: Long) {
        exposedJdbcTemplate.update(UPSERT_FESTIVAL_PERIOD_SQL, catalogId)
        exposedJdbcTemplate.update(DELETE_ORPHAN_FESTIVAL_SQL, catalogId)
    }

    /** 상태가 실제로 바뀐 행만 갱신하고, 반영 후 노출 중인 것 전부를 돌려준다. */
    override fun refreshTranslationVisibility(): VisibilityRefreshResult {
        // 그 언어의 원본이 있는가 = 노출 여부.
        val originExists = exists(
            SpotTable.select(SpotTable.id).where {
                (SpotTable.catalogId eq SpotCatalogTranslationTable.catalogId) and (SpotTable.language eq SpotCatalogTranslationTable.language)
            },
        )

        val hidden = SpotCatalogTranslationTable.update({ (SpotCatalogTranslationTable.visible eq true) and not(originExists) }) {
            it[SpotCatalogTranslationTable.visible] = false
        }
        val shown = SpotCatalogTranslationTable.update({ (SpotCatalogTranslationTable.visible eq false) and originExists }) {
            it[SpotCatalogTranslationTable.visible] = true
        }
        return VisibilityRefreshResult(hiddenCount = hidden, shownCount = shown, visible = findVisible())
    }

    /** 시군구 귀속은 geography 연산이라 raw SQL이다(V75). */
    override fun assignUnassignedDistricts(): Int = exposedJdbcTemplate.update(ASSIGN_DISTRICT_SQL)

    override fun countDistrictUnassigned(): Int = exposedJdbcTemplate.queryForObject(COUNT_DISTRICT_UNASSIGNED_SQL, Int::class.java) ?: 0

    /** 지금 노출 중인 (카탈로그, 언어) 전부. 구독자가 사본을 맞추는 정본이다. */
    private fun findVisible(): List<CatalogLanguage> = SpotCatalogTranslationTable
        .select(SpotCatalogTranslationTable.catalogId, SpotCatalogTranslationTable.language)
        .where { SpotCatalogTranslationTable.visible eq true }
        .map { CatalogLanguage(it[SpotCatalogTranslationTable.catalogId], DomainLocale.valueOf(it[SpotCatalogTranslationTable.language])) }

    private companion object {
        /**
         * 카탈로그 좌표를 품은 시군구를 채운다.
         * - 컬럼을 `::geometry`로 캐스팅하지 않는다 — GiST 인덱스가 죽는다.
         * - 바다·경계 밖 좌표는 매칭되지 않아 매번 다시 시도된다.
         * - 경계에 걸친 좌표는 `UPDATE ... FROM`이 인접 시군구 중 임의의 한 행을 쓴다.
         */
        const val ASSIGN_DISTRICT_SQL = """
            UPDATE tour.spot_catalog cat
            SET district_code = d.code,
                updated_at    = now()
            FROM tour.district d
            WHERE cat.district_code IS NULL
              AND ST_Intersects(d.boundary, cat.location)
        """

        const val COUNT_DISTRICT_UNASSIGNED_SQL = """
            SELECT count(*) FROM tour.spot_catalog WHERE district_code IS NULL
        """

        /**
         * 번역 upsert. `catalog_id`가 두 번(값 자리·SELECT의 WHERE) 들어간다.
         * - 사본 컬럼(map_x·map_y·category)은 충돌 시에도 갱신하고, 제목·주소는 그대로 둔다(R-4).
         * - `map_x`·`map_y`를 갱신하면 트리거(V52)가 `location`도 고친다.
         */
        val UPSERT_TRANSLATION_SQL = """
            INSERT INTO spot_catalog_translation (catalog_id, language, title, address, map_x, map_y, category, visible)
            SELECT ?, ?, ?, ?, c.map_x, c.map_y, c.category, TRUE
            FROM spot_catalog c WHERE c.id = ?
            ON CONFLICT (catalog_id, language) DO UPDATE SET
                map_x      = EXCLUDED.map_x,
                map_y      = EXCLUDED.map_y,
                category   = EXCLUDED.category,
                -- **행이 다시 생겼다는 것은 그 언어의 원본이 돌아왔다는 뜻이다.** 감춰져
                -- 있었다면 여기서 되살린다 — 노출 갱신을 기다리지 않는다.
                visible    = TRUE,
                updated_at = now()
            WHERE spot_catalog_translation.map_x    IS DISTINCT FROM EXCLUDED.map_x
               OR spot_catalog_translation.map_y    IS DISTINCT FROM EXCLUDED.map_y
               OR spot_catalog_translation.category IS DISTINCT FROM EXCLUDED.category
               OR NOT spot_catalog_translation.visible
        """.trimIndent()

        /**
         * 축제 기간 upsert(KO 우선). 원본 `spot_festival`과는 `spot_id`로 잇는다.
         * - 축제 행이 없으면 0행을 쓴다 — 삭제는 다음 문장이 한다.
         */
        val UPSERT_FESTIVAL_PERIOD_SQL = """
            INSERT INTO spot_catalog_festival (catalog_id, event_start_date, event_end_date)
            SELECT DISTINCT ON (s.catalog_id) s.catalog_id, f.event_start_date, f.event_end_date
            FROM spot s
            JOIN spot_festival f ON f.spot_id = s.id
            WHERE s.catalog_id = ?
            ORDER BY s.catalog_id,
                     CASE s.language WHEN 'KO' THEN 0 WHEN 'EN' THEN 1 WHEN 'JA' THEN 2 ELSE 3 END,
                     s.id
            ON CONFLICT (catalog_id) DO UPDATE SET
                event_start_date = EXCLUDED.event_start_date,
                event_end_date   = EXCLUDED.event_end_date,
                updated_at       = now()
        """.trimIndent()

        /** 축제 행이 사라진 카탈로그의 기간을 지운다. 위의 upsert 다음에 와야 한다 — 뒤집히면 방금 쓴 기간을 지운다. */
        val DELETE_ORPHAN_FESTIVAL_SQL = """
            DELETE FROM spot_catalog_festival cf
            WHERE cf.catalog_id = ?
              AND NOT EXISTS (
                  SELECT 1
                  FROM spot s
                  JOIN spot_festival f ON f.spot_id = s.id
                  WHERE s.catalog_id = cf.catalog_id
              )
        """.trimIndent()
    }
}
