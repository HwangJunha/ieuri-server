package org.around.ieuri.architecture

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 이번 스펙이 추가한 마이그레이션의 정적 검사(SPEC-0023 AC-3·AC-4·AC-5·AC-10).
 *
 * **이번 슬라이스가 추가한 파일만 본다.** 과거 마이그레이션은 FK도 트리거 함수도 정당하게
 * 갖고 있으며, 그것을 규칙 위반으로 세면 이 테스트는 아무것도 지키지 못한다.
 */
class MigrationRuleTest {

    private val addedMigrations = listOf(
        Path.of("src/main/resources/db/migration/V61__split_context_schemas.sql"),
        Path.of("src/main/resources/db/search-node/V8__split_tour_schema.sql"),
    )

    private val sources: Map<String, String> by lazy {
        addedMigrations.associate { it.name to it.readText() }
    }

    @Test
    fun `이번 슬라이스의 마이그레이션 파일이 존재한다`() {
        val missing = addedMigrations.filterNot { it.toFile().exists() }
        assertTrue(missing.isEmpty(), "찾지 못한 마이그레이션: $missing")
    }

    /**
     * AC-3·AC-10 — FK 신설 0건. 컨텍스트 간 FK 금지(R4)는 스키마를 나누는 이 작업에서 특히
     * 지켜져야 한다. `community_post_spot.catalog_id` 정리는 FK가 아니라 애플리케이션
     * 계층(R11)이 맡는다.
     */
    @Test
    fun `추가한 마이그레이션은 FK를 신설하지 않는다`() {
        sources.forEach { (file, sql) ->
            val hits = statementsOf(sql).filter { it.contains("FOREIGN KEY", ignoreCase = true) }
            assertEquals(emptyList(), hits, "$file 에 FK 신설이 있다")
        }
    }

    /** AC-5 — `CREATE EXTENSION` 0건. 확장 활성화는 CNPG 매니페스트가 소유한다(POL-034). */
    @Test
    fun `추가한 마이그레이션은 확장을 만들지 않는다`() {
        sources.forEach { (file, sql) ->
            val hits = statementsOf(sql).filter { it.contains("CREATE EXTENSION", ignoreCase = true) }
            assertEquals(emptyList(), hits, "$file 에 CREATE EXTENSION이 있다")
        }
    }

    /**
     * AC-4 — 트리거 함수의 소유 스키마가 그 트리거가 붙은 테이블의 스키마와 같다.
     * 어긋나면 한 컨텍스트가 다른 컨텍스트 스키마의 함수에 의존하게 되고, 그것이 이 스펙이
     * 제거하려는 결합이다(R6).
     */
    @Test
    fun `트리거 함수는 대상 테이블의 소유 스키마로 옮겨진다`() {
        val expected = mapOf(
            "spot_sync_location" to "tour",
            "spot_catalog_sync_location" to "tour",
            "community_post_spot_sync_location" to "community",
        )

        val sql = sources.getValue("V61__split_context_schemas.sql")
        val moved = Regex(
            """ALTER\s+FUNCTION\s+(\w+)\s*\(\s*\)\s+SET\s+SCHEMA\s+(\w+)""",
            RegexOption.IGNORE_CASE,
        ).findAll(sql).associate { it.groupValues[1] to it.groupValues[2] }

        assertEquals(expected, moved, "트리거 함수의 이동 대상 스키마가 배치와 어긋난다")
    }

    /**
     * 테이블은 이동만 하고 이름은 바꾸지 않는다(R1). 두 가지를 같이 하면 복제 갱신이 두 배로
     * 어려워지며, publication 대상 추적이 사실상 불가능해진다.
     */
    @Test
    fun `추가한 마이그레이션은 테이블 이름을 바꾸지 않는다`() {
        sources.forEach { (file, sql) ->
            val hits = statementsOf(sql).filter { it.contains("RENAME TO", ignoreCase = true) }
            assertEquals(emptyList(), hits, "$file 에 이름 변경이 있다")
        }
    }

    /**
     * 주석을 제거하고 문장 단위로 자른다. 주석 안의 `FOREIGN KEY`·`CREATE EXTENSION` 언급을
     * 위반으로 세지 않기 위한 것이다 — 이 저장소의 마이그레이션은 주석이 길고, 바로 그
     * 금지 사항을 설명하는 주석이 실재한다.
     */
    private fun statementsOf(sql: String): List<String> =
        sql.lineSequence()
            .map { it.substringBefore("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
