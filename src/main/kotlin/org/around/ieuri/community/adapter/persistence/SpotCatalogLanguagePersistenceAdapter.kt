package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.table.SpotCatalogLanguageTable
import org.around.ieuri.community.application.catalog.required.SpotCatalogLanguageRepository
import org.around.ieuri.community.domain.catalog.CatalogLanguage
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.springframework.stereotype.Repository

/** 카탈로그-언어 사본. 비우고 다시 넣지 않는다 — 중간에 표가 비면 동시 조회가 글을 놓친다. */
@Repository
class SpotCatalogLanguagePersistenceAdapter : SpotCatalogLanguageRepository {

    /**
     * 표 전체를 [entries]로 맞춘다. 지금 있는 행을 읽어 차집합만 움직인다.
     * - 주의: [entries]가 비면 표를 전부 지운다.
     */
    override fun replaceAll(entries: List<CatalogLanguage>): Int {
        val resolved = entries.mapTo(HashSet()) { it.catalogId to it.language.name }
        val stored = SpotCatalogLanguageTable.select(SpotCatalogLanguageTable.catalogId, SpotCatalogLanguageTable.language)
            .mapTo(HashSet()) { it[SpotCatalogLanguageTable.catalogId] to it[SpotCatalogLanguageTable.language] }

        // 정본에 없는 사본 행을 지운다. 언어마다 묶고, 한 문장의 파라미터 수(PostgreSQL 한도 32,767) 아래로 나눈다.
        (stored - resolved).groupBy({ it.second }) { it.first }.forEach { (language, catalogIds) ->
            catalogIds.chunked(MAX_IDS_PER_STATEMENT).forEach { chunk ->
                SpotCatalogLanguageTable.deleteWhere { (SpotCatalogLanguageTable.language eq language) and (SpotCatalogLanguageTable.catalogId inList chunk) }
            }
        }
        // 사본에 없는 정본 행을 넣는다. 동시에 같은 행이 들어와도 유일 제약에 걸리지 않는다(`ON CONFLICT DO NOTHING`).
        SpotCatalogLanguageTable.batchInsert(resolved - stored, ignore = true, shouldReturnGeneratedValues = false) { (catalogId, language) ->
            this[SpotCatalogLanguageTable.catalogId] = catalogId
            this[SpotCatalogLanguageTable.language] = language
        }
        return entries.size
    }

    private companion object {
        const val MAX_IDS_PER_STATEMENT = 10_000
    }
}
