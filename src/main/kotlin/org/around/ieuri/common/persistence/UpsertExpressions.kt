package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.IsDistinctFromOp
import org.jetbrains.exposed.v1.core.case
import org.jetbrains.exposed.v1.core.compoundOr
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.core.statements.insertValue
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import java.time.OffsetDateTime

/**
 * upsert의 `DO UPDATE`에서 [columns] 중 하나라도 값이 바뀔 때만 `now()`, 아니면 기존 [updatedAt]을 내는 식.
 * - 같은 값으로 다시 적재해도 `updated_at`이 움직이지 않는다 — "바뀐 시각"을 지킨다.
 */
fun touchedWhenChanged(updatedAt: Column<OffsetDateTime>, columns: List<Column<*>>): Expression<OffsetDateTime> = case()
    .When(columns.map { IsDistinctFromOp(it, insertValue(it)) }.compoundOr(), CurrentTimestampWithTimeZone)
    .Else(updatedAt)

/** `DO UPDATE SET col = EXCLUDED.col`. 열 목록을 돌며 쓰려고 타입을 지운다. */
@Suppress("UNCHECKED_CAST")
fun UpdateStatement.setFromInsert(column: Column<*>) {
    val typed = column as Column<Any?>
    this[typed] = insertValue(typed)
}
