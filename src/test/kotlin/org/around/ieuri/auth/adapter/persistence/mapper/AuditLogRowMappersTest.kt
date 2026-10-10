package org.around.ieuri.auth.adapter.persistence.mapper

import org.around.ieuri.auth.adapter.persistence.table.MemberAccessLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberAuditLogTable
import org.around.ieuri.auth.adapter.persistence.table.MemberLocationAccessLogTable
import org.around.ieuri.auth.domain.audit.LocationAccessLog
import org.around.ieuri.auth.domain.audit.MemberAccessLog
import org.around.ieuri.auth.domain.audit.MemberAuditLog
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.InternalApi
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.InsertStatement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 감사 기록 세 종의 도메인 → 행 변환(POL-033). id와 시각은 DB가 채우므로 쓰지 않는다. 쓴 값은 내부 API인 `values`로만 읽힌다. */
@OptIn(InternalApi::class)
class AuditLogRowMappersTest {

    private fun row(table: Table) = InsertStatement<Number>(table)

    /** 쓴 열이 정확히 [columns]뿐인지 — DB가 채우는 열이나 이번 쓰기와 무관한 열을 건드리지 않는다. */
    private fun assertWrites(row: InsertStatement<Number>, vararg columns: Column<*>) {
        assertEquals<Set<Column<*>>>(columns.toSet(), row.values.keys)
    }

    @Test
    fun `회원 감사 기록이 행으로 옮겨진다 - payload는 넘겨받은 JSON을 쓴다`() {
        val row = row(MemberAuditLogTable)

        MemberAuditLog(memberId = 11L, eventType = "MEMBER_SUSPENDED", payload = mapOf("reason" to "도메인-값")).writeTo(row, payloadJson = """{"reason":"직렬화-값"}""")

        assertEquals(11L, row.values[MemberAuditLogTable.memberId])
        assertEquals("MEMBER_SUSPENDED", row.values[MemberAuditLogTable.eventType])
        assertEquals("""{"reason":"직렬화-값"}""", row.values[MemberAuditLogTable.payload])
        assertWrites(row, MemberAuditLogTable.memberId, MemberAuditLogTable.eventType, MemberAuditLogTable.payload)
    }

    @Test
    fun `접속기록이 행으로 옮겨진다 - 수행업무와 접속지가 섞이지 않는다`() {
        val row = row(MemberAccessLogTable)

        MemberAccessLog(memberId = 21L, operation = "me", ipAddress = "203.0.113.7").writeTo(row)

        assertEquals(21L, row.values[MemberAccessLogTable.memberId])
        assertEquals("me", row.values[MemberAccessLogTable.operation])
        assertEquals("203.0.113.7", row.values[MemberAccessLogTable.ipAddress])
        assertWrites(row, MemberAccessLogTable.memberId, MemberAccessLogTable.operation, MemberAccessLogTable.ipAddress)
    }

    @Test
    fun `접속지를 모르는 접속기록은 접속지 열에 null을 쓴다`() {
        val row = row(MemberAccessLogTable)

        MemberAccessLog(memberId = 22L, operation = "members", ipAddress = null).writeTo(row)

        assertNull(row.values[MemberAccessLogTable.ipAddress])
        assertEquals("members", row.values[MemberAccessLogTable.operation])
        assertEquals(true, MemberAccessLogTable.ipAddress in row.values)
    }

    @Test
    fun `위치 조회 기록이 행으로 옮겨진다`() {
        val row = row(MemberLocationAccessLogTable)

        LocationAccessLog(memberId = 31L, payload = mapOf("operation" to "도메인-값")).writeTo(row, payloadJson = """{"operation":"nearbySpots"}""")

        assertEquals(31L, row.values[MemberLocationAccessLogTable.memberId])
        assertEquals("""{"operation":"nearbySpots"}""", row.values[MemberLocationAccessLogTable.payload])
        assertWrites(row, MemberLocationAccessLogTable.memberId, MemberLocationAccessLogTable.payload)
    }
}
