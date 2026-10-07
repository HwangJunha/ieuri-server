package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

/** tourism 카탈로그의 활성 언어 복제본. tourism 이벤트로 전량 교체된다. */
object SpotCatalogLanguageTable : Table("community.community_spot_catalog_language") {
    val catalogId = long("catalog_id")
    val language = varchar("language", 2)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(catalogId, language, name = "pk_community_spot_catalog_language")

    init {
        check("ck_community_spot_catalog_language_language") { language inList listOf("EN", "KO", "JA", "ZH") }
    }
}
