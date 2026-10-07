package org.around.ieuri.architecture

import com.tngtech.archunit.core.domain.JavaClass
import jakarta.persistence.Entity
import jakarta.persistence.Table
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.core.Table as ExposedTable

/**
 * 스키마 분할의 배치표 검증(SPEC-0023). 실제 DB 배치는 부팅 시 `ddl-auto: validate`가 잡는다.
 */
class SchemaPlacementTest {

    /** 스펙 §4 배치표. 테이블 → 소유 스키마. 배치 메타 6개는 JPA 엔티티가 없어 제외한다. */
    private val placement: Map<String, String> = buildMap {
        listOf(
            "member", "member_profile", "member_terms_agreement", "terms", "email_verification",
            "password_reset", "member_audit_log", "role", "permission", "role_permission", "member_role",
            // 위치 조회 감사(SPEC-0032)
            "member_location_access_log",
            // 접속기록(안전성 확보조치 기준 제8조)
            "member_access_log",
        ).forEach { put(it, "auth") }

        listOf(
            "community_post", "community_post_photo", "community_post_like", "community_post_hide",
            "community_post_tag", "community_post_spot", "community_post_chip", "community_comment",
            "community_block", "community_report", "community_conversation", "community_message",
            "community_message_report", "community_message_report_item", "community_orphan_photo_object",
            "community_message_quote",
            // 장소 질문/답변(SPEC-0026)
            "community_spot_question", "community_spot_answer", "community_spot_answer_reply",
            "community_spot_qa_photo",
            // 카탈로그가 해소되는 언어의 커뮤니티 사본(SPEC-0029 R-15)
            "community_spot_catalog_language",
        ).forEach { put(it, "community") }

        listOf(
            "spot", "spot_catalog", "spot_catalog_translation", "spot_catalog_festival", "spot_detail",
            "spot_detail_image", "spot_detail_intro", "spot_detail_info", "spot_festival", "spot_reaction",
            "spot_embedding",
            "congestion_area", "district", "kto_visitor_count", "kto_concentration_rate",
            "kto_stay_intensity", "kto_spending_intensity",
            // 전국 지도(SPEC-0030). map_cell은 JPA 엔티티가 없다(생 SQL 어댑터 전용).
            "member_district_visit", "map_share_link", "map_cell_image",
            "area", "map_cell", "map_cell_district", "map_relation",
        ).forEach { put(it, "tour") }

        listOf("category", "category_translation", "category_external_mapping", "app_category")
            .forEach { put(it, "category") }

        // 알림(SPEC-0028)
        listOf("device_token", "notification_preference", "notification_message")
            .forEach { put(it, "alarm") }
    }

    /** JPA 엔티티도 Exposed 테이블 선언도 없는 테이블 — raw SQL로만 접근하거나, 쓰는 코드가 없다. */
    private val tablesWithoutEntity = setOf(
        // 고아 사진 판정을 지우며 테이블만 남겼다(SPEC-0039 D-45). 쓰는 코드가 없다.
        "community_orphan_photo_object",
    )

    private val entities: List<JavaClass> = ArchitectureClasses.main
        .filter { it.isAnnotatedWith(Entity::class.java) }

    /** Exposed로 옮긴 context의 `Table` 선언(object). 이름은 `스키마.테이블`이다. */
    private val exposedTables: Map<String, String> = ArchitectureClasses.main
        .filter { it.isAssignableTo(ExposedTable::class.java) && it.name.endsWith("Table") }
        .mapNotNull { Class.forName(it.name).kotlin.objectInstance as? ExposedTable }
        .associate { it.tableName.substringAfter('.') to it.tableName.substringBefore('.', missingDelimiterValue = "") }

    /** AC-6 — 전량 배정, 중복 0, 미배정 0. Map이므로 중복 키는 구조적으로 불가능하다. */
    @Test
    fun `배치표는 애플리케이션 테이블을 빠짐없이 배정한다`() {
        assertEquals(65, placement.size, "배치표에 등록된 애플리케이션 테이블 수")
        assertEquals(
            setOf("auth", "community", "tour", "category", "alarm"),
            placement.values.toSet(),
            "배치 대상 스키마는 5개다 — batch는 JPA 엔티티가 없어 이 표에 없다",
        )
    }

    /** AC-1 — 모든 엔티티가 비어 있지 않은, 허용된 스키마 이름을 갖는다. */
    @Test
    fun `모든 JPA 엔티티가 스키마를 명시한다`() {
        val allowed = setOf("auth", "community", "tour", "category", "alarm")

        val missing = entities.filter { schemaOf(it).isBlank() }.map { it.simpleName }
        assertTrue(missing.isEmpty(), "스키마를 명시하지 않은 엔티티: $missing")

        val unknown = entities.filterNot { schemaOf(it) in allowed }.map { "${it.simpleName}=${schemaOf(it)}" }
        assertTrue(unknown.isEmpty(), "허용되지 않은 스키마를 쓰는 엔티티: $unknown")
    }

    /** AC-2 — 각 엔티티의 (스키마, 테이블) 쌍이 배치표와 일치하고, 배치표에 없는 테이블에 매핑된 엔티티가 없다. */
    @Test
    fun `엔티티의 스키마와 테이블 쌍이 배치표와 일치한다`() {
        val mismatched = entities.mapNotNull { entity ->
            val table = tableOf(entity)
            val expected = placement[table]
                ?: return@mapNotNull "${entity.simpleName}: 배치표에 없는 테이블 '$table'"
            val actual = schemaOf(entity)
            if (actual == expected) null else "${entity.simpleName}($table): 기대 '$expected', 실제 '$actual'"
        }

        assertTrue(mismatched.isEmpty(), "배치표와 어긋나는 엔티티:\n${mismatched.joinToString("\n")}")
    }

    /** Exposed 테이블 선언도 스키마를 명시하고, 그 (스키마, 테이블) 쌍이 배치표와 일치한다. */
    @Test
    fun `Exposed 테이블 선언의 스키마와 테이블 쌍이 배치표와 일치한다`() {
        assertTrue(exposedTables.isNotEmpty(), "Exposed 테이블 선언을 찾지 못했다 — 탐색 조건이 틀렸다")
        val mismatched = exposedTables.mapNotNull { (table, schema) ->
            val expected = placement[table] ?: return@mapNotNull "배치표에 없는 테이블 '$table'"
            if (schema == expected) null else "$table: 기대 '$expected', 실제 '$schema'"
        }

        assertTrue(mismatched.isEmpty(), "배치표와 어긋나는 Exposed 테이블:\n${mismatched.joinToString("\n")}")
    }

    /** 매핑 없는 테이블을 명시 목록으로 고정한다 — 위 테스트들은 존재하는 선언만 보므로 매핑 누락을 못 잡는다. */
    @Test
    fun `엔티티 없는 테이블은 raw SQL 전용뿐이다`() {
        val mappedTables = entities.map { tableOf(it) }.toSet() + exposedTables.keys
        val actuallyMissing = placement.keys - mappedTables

        assertEquals(
            tablesWithoutEntity,
            actuallyMissing,
            "엔티티가 없는 테이블 목록이 달라졌다 — 새 엔티티를 추가했거나 매핑이 빠졌다",
        )
    }

    private fun tableOf(entity: JavaClass): String = entity.tryGetAnnotationOfType(Table::class.java).map { it.name }.orElse("")

    private fun schemaOf(entity: JavaClass): String = entity.tryGetAnnotationOfType(Table::class.java).map { it.schema }.orElse("")
}
