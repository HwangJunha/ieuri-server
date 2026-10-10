package org.around.ieuri.auth.adapter.persistence

import org.around.ieuri.auth.adapter.persistence.table.MemberTable
import org.jetbrains.exposed.v1.core.Table
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `member`를 참조하는 FK 자식이 전부 `MemberPersistenceAdapter.delete`에서 지워지는지 Exposed 테이블 정의와 대조한다(DB 없음).
 * - 하나라도 빠지면 그 테이블에 행을 만든 회원만 탈퇴가 영구히 막혀 평소에 드러나지 않는다.
 * - 자식 목록은 테이블 객체의 FK에서 읽는다. 목록을 손으로 적으면 새 테이블을 더할 때 이 테스트도 함께 잊힌다.
 */
class MemberDeletionCoverageTest {

    private val tablePackage = "org.around.ieuri.auth.adapter.persistence.table"
    private val tableDir = File("src/main/kotlin/org/around/ieuri/auth/adapter/persistence/table")
    private val adapterSource = File("src/main/kotlin/org/around/ieuri/auth/adapter/persistence/MemberPersistenceAdapter.kt")

    /** `table/`의 Exposed 테이블 객체 전부. 파일 하나에 타입 하나라 파일 이름이 곧 객체 이름이다. */
    private fun authTables(): List<Table> = tableDir.listFiles { file -> file.extension == "kt" }
        .orEmpty()
        .mapNotNull { Class.forName("$tablePackage.${it.nameWithoutExtension}").getField("INSTANCE").get(null) as? Table }

    /** `member`로 향하는 FK를 가진 테이블의 객체 이름. */
    private fun tablesReferencingMember(): Set<String> = authTables()
        .filter { table -> table.foreignKeys.any { it.targetTable == MemberTable } }
        .map { it.javaClass.simpleName }
        .toSet()

    /** `delete` 본문이 `deleteWhere`를 부르는 테이블 객체 이름을 부른 순서대로. */
    private fun tablesDeletedInOrder(): List<String> {
        val source = adapterSource.readText()
        assertTrue(source.contains(DELETE_SIGNATURE), "MemberPersistenceAdapter에서 `$DELETE_SIGNATURE`를 찾지 못했다 — 이름이 바뀌었으면 이 테스트를 함께 고친다.")
        val body = source.substringAfter(DELETE_SIGNATURE).substringBefore("\n    }")
        return Regex("""(\w+)\.deleteWhere\b""").findAll(body).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `member를 참조하는 모든 테이블이 회원 삭제에서 지워진다`() {
        val referencing = tablesReferencingMember()
        // 테이블을 읽지 못했다면 이 테스트가 무의미해지므로 먼저 막는다.
        assertTrue(referencing.size >= 5, "member를 참조하는 테이블을 찾지 못했다: $referencing")

        val missing = referencing - tablesDeletedInOrder().toSet()

        assertTrue(
            missing.isEmpty(),
            "[회원 삭제] MemberPersistenceAdapter.delete가 지우지 않는 FK 자식이 있다: $missing\n" +
                "  → delete 본문에 `XxxTable.deleteWhere { XxxTable.memberId eq memberId }`를 MemberTable 삭제 앞에 넣는다. " +
                "빠지면 그 테이블에 행이 있는 회원은 탈퇴가 FK 제약에 걸려 영구히 막힌다.",
        )
    }

    @Test
    fun `회원 행은 자식을 모두 지운 뒤 마지막에 지운다`() {
        val deleted = tablesDeletedInOrder()

        assertEquals(
            MemberTable.javaClass.simpleName,
            deleted.lastOrNull(),
            "[회원 삭제] MemberPersistenceAdapter.delete는 MemberTable을 마지막에 지워야 한다(지금 순서: $deleted)\n" +
                "  → `MemberTable.deleteWhere`를 delete 본문의 맨 끝으로 옮긴다. 자식이 남은 채로 회원을 먼저 지우면 FK 제약에 걸린다.",
        )
        assertEquals(
            1,
            deleted.count { it == MemberTable.javaClass.simpleName },
            "[회원 삭제] MemberTable 삭제는 한 번이어야 한다(지금 순서: $deleted)\n  → 중복된 `MemberTable.deleteWhere`를 지운다.",
        )
    }

    private companion object {
        const val DELETE_SIGNATURE = "override fun delete(memberId: Long) {"
    }
}
