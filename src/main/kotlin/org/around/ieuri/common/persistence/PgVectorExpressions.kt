package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.CustomOperator
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.stringParam

/**
 * pgvector `vector` 컬럼. 값은 `[0.1,0.2,...]` 텍스트로 오가며, 쓸 때는 [vectorParam]으로 캐스팅한다.
 * - 드라이버가 문자열 파라미터를 `varchar`로 보내 컬럼에 바로 넣으면 타입 오류다 — 캐스트를 거친다.
 */
class VectorColumnType : ColumnType<String>() {
    override fun sqlType(): String = "vector"

    override fun valueFromDB(value: Any): String = value.toString()
}

fun Table.vector(name: String): Column<String> = registerColumn(name, VectorColumnType())

/** 벡터 값 파라미터(`?::vector`). */
fun vectorParam(vector: FloatArray): Expression<String> = stringParam(vector.joinToString(",", "[", "]")).castTo(VectorColumnType())

/** `a <=> b` — 코사인 거리(0~2). 유사도는 `1 - 거리`다. */
infix fun Expression<String>.cosineDistance(other: Expression<String>): ExpressionWithColumnType<Double> = CustomOperator("<=>", DoubleColumnType(), this, other)

/** pgvector `avg(vector)` — 벡터의 성분별 평균. 행이 없으면 null이다. */
fun vectorAvg(vector: Expression<String>): ExpressionWithColumnType<String?> = CustomFunction("avg", VectorColumnType(), vector)
