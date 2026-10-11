package org.around.ieuri.architecture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.exposed.v1.core.Table as ExposedTable

/**
 * 스키마 분할의 배치표 검증(SPEC-0023). 선언과 실제 DB의 어긋남은 기동 때 검사하지 않는다 — 그 테이블을 처음 읽는 쿼리에서 드러난다.
 */
class SchemaPlacementTest {

    /** 스펙 §4 배치표. 테이블 → 소유 스키마. 배치 메타 6개는 테이블 선언이 없어 제외한다. */
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
            // 전국 지도(SPEC-0030)
            "member_district_visit", "map_share_link", "map_cell_image",
            "area", "map_cell", "map_cell_district", "map_relation",
        ).forEach { put(it, "tour") }

        listOf("category", "category_translation", "category_external_mapping", "app_category")
            .forEach { put(it, "category") }

        // 알림(SPEC-0028)
        listOf("device_token", "notification_preference", "notification_message")
            .forEach { put(it, "alarm") }
    }

    /** Exposed 테이블 선언이 없는 테이블 — 쓰는 코드가 없다. */
    private val tablesWithoutDeclaration = setOf(
        // 고아 사진 판정을 지우며 테이블만 남겼다(SPEC-0039 D-45). 쓰는 코드가 없다.
        "community_orphan_photo_object",
    )

    /** `Table` 선언(object). 이름은 `스키마.테이블`이다. */
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
            "배치 대상 스키마는 5개다 — batch는 테이블 선언이 없어 이 표에 없다",
        )
    }

    /** AC-1·AC-2 — 테이블 선언이 스키마를 명시하고, 그 (스키마, 테이블) 쌍이 배치표와 일치한다. */
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
    fun `선언 없는 테이블은 목록에 적힌 것뿐이다`() {
        assertEquals(
            tablesWithoutDeclaration,
            placement.keys - exposedTables.keys,
            "테이블 선언이 없는 테이블 목록이 달라졌다 — 선언을 추가했거나 빠뜨렸다",
        )
    }
}
