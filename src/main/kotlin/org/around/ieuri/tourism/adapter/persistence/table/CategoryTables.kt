package org.around.ieuri.tourism.adapter.persistence.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.regexp
import org.jetbrains.exposed.v1.core.trim
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

// category 스키마. 원본은 마이그레이션이다 — 컬럼·제약·인덱스 이름을 마이그레이션과 같게 둔다.

object CategoryTable : Table("category.category") {
    val code = varchar("code", 30)
    val iconUrl = varchar("icon_url", 500).nullable()
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val id = long("id").autoIncrement()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_category_code", code)
        check("ck_category_code_format") { code.regexp("^[A-Z][A-Z0-9_]*$") }
        check("ck_category_code_not_blank") { code.trim() neq "" }
    }
}

object CategoryTranslationTable : Table("category.category_translation") {
    val language = varchar("language", 2)
    val label = varchar("label", 50)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val categoryId = long("category_id")
        .references(CategoryTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_category_translation_category")
    override val primaryKey = PrimaryKey(categoryId, language)

    init {
        check("ck_category_translation_label_not_blank") { label.trim() neq "" }
        check("ck_category_translation_language") { language inList listOf("EN", "KO", "JA", "ZH") }
    }
}

object CategoryExternalMappingTable : Table("category.category_external_mapping") {
    val id = long("id").autoIncrement()
    val categoryId = long("category_id")
        .references(CategoryTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_category_external_mapping_category")
    val provider = varchar("provider", 30)
    val codeset = varchar("codeset", 20)
    val externalCode = varchar("external_code", 30)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_category_external_code", provider, codeset, externalCode)
        uniqueIndex("uq_category_external_mapping", provider, codeset, categoryId)
        index("idx_category_external_mapping_lookup", false, provider, codeset, externalCode)
    }
}

/** 앱별로 노출하는 카테고리. `active`의 DB 제약 이름은 옛 열 이름(`enabled`)을 따른다. */
object AppCategoryTable : Table("category.app_category") {
    val id = long("id").autoIncrement()
    val appKey = varchar("app_key", 50)
    val language = varchar("language", 2)
    val active = bool("active").default(true)
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    val updatedAt = timestampWithTimeZone("updated_at").databaseGenerated()
    val categoryId = long("category_id")
        .references(CategoryTable.id, ReferenceOption.CASCADE, ReferenceOption.NO_ACTION, "fk_app_category_category")
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_app_category", appKey, language, categoryId)
        index("idx_app_category_app_language", false, appKey, language, categoryId)
        check("ck_app_category_app_key_not_blank") { appKey.trim() neq "" }
        check("ck_app_category_language") { language inList listOf("EN", "KO", "JA", "ZH") }
    }
}
