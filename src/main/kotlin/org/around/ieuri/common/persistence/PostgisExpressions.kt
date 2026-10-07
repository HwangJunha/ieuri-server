package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.CustomOperator
import org.jetbrains.exposed.v1.core.DoubleColumnType
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.QueryBuilder
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.castTo
import org.jetbrains.exposed.v1.core.doubleParam
import org.jetbrains.exposed.v1.core.intParam

// PostGIS 함수를 Exposed 식으로 쓴다. 이름은 SQL 함수 이름을 그대로 따른다.
// - 거리·반경은 geography끼리 재면 미터다. geometry(`asGeometry`)끼리는 좌표 단위(도)다.
// - 인덱스가 걸린 geography 컬럼을 `::geometry`로 캐스팅하면 GiST 인덱스를 못 탄다 — 필요한 쪽(상수)을 캐스팅한다.

/** `ST_MakePoint(lon, lat)::geography`. 순서가 (경도, 위도)다. */
fun geographyPoint(longitude: Double, latitude: Double): Expression<String> = CustomFunction<String>("ST_MakePoint", GeometryColumnType(), doubleParam(longitude), doubleParam(latitude)).castTo(GeographyColumnType())

/** `ST_MakeEnvelope(minX, minY, maxX, maxY, 4326)` — geometry 사각형. */
fun envelope(minLongitude: Double, minLatitude: Double, maxLongitude: Double, maxLatitude: Double): Expression<String> = CustomFunction(
    "ST_MakeEnvelope",
    GeometryColumnType(),
    doubleParam(minLongitude),
    doubleParam(minLatitude),
    doubleParam(maxLongitude),
    doubleParam(maxLatitude),
    intParam(WGS84),
)

fun Expression<*>.asGeography(): ExpressionWithColumnType<String> = castTo(GeographyColumnType())

fun Expression<*>.asGeometry(): ExpressionWithColumnType<String> = castTo(GeometryColumnType())

/** `ST_Distance(a, b)` — geography면 미터. */
fun stDistance(a: Expression<String>, b: Expression<String>): ExpressionWithColumnType<Double> = CustomFunction("ST_Distance", DoubleColumnType(), a, b)

/** `ST_DWithin(a, b, meters)` — 인덱스를 타는 반경 조건. */
fun stDWithin(a: Expression<String>, b: Expression<String>, distance: Double): Op<Boolean> = BooleanFunctionOp("ST_DWithin", a, b, doubleParam(distance))

fun stIntersects(a: Expression<String>, b: Expression<String>): Op<Boolean> = BooleanFunctionOp("ST_Intersects", a, b)

/** `ST_X(geometry)`. */
fun stX(geometry: Expression<String>): ExpressionWithColumnType<Double> = CustomFunction("ST_X", DoubleColumnType(), geometry)

/** `ST_Y(geometry)`. */
fun stY(geometry: Expression<String>): ExpressionWithColumnType<Double> = CustomFunction("ST_Y", DoubleColumnType(), geometry)

fun stAsGeoJson(geometry: Expression<String>): ExpressionWithColumnType<String> = CustomFunction("ST_AsGeoJSON", TextColumnType(), geometry)

/** `a && b` — 경계 상자 겹침. GiST 인덱스를 타는 1차 거름이다. */
infix fun Expression<String>.boxOverlaps(other: Expression<String>): Op<Boolean> = BooleanOperatorOp("&&", this, other)

/** `a <-> b` — KNN 거리 연산자. `ORDER BY`에 두면 GiST 인덱스로 가까운 순을 낸다. */
infix fun Expression<String>.knn(other: Expression<String>): ExpressionWithColumnType<Double> = CustomOperator("<->", DoubleColumnType(), this, other)

private const val WGS84 = 4326

/** 불리언을 돌려주는 SQL 이항 연산자. */
internal class BooleanOperatorOp(
    private val operator: String,
    private val left: Expression<*>,
    private val right: Expression<*>,
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append('(')
        append(left)
        append(" $operator ")
        append(right)
        append(')')
    }
}

/** 불리언을 돌려주는 SQL 함수를 `WHERE`에 바로 쓰는 조건으로. `= TRUE`를 붙이지 않는다. */
internal class BooleanFunctionOp(
    private val name: String,
    private vararg val operands: Expression<*>,
) : Op<Boolean>() {
    override fun toQueryBuilder(queryBuilder: QueryBuilder): Unit = queryBuilder {
        append(name)
        append('(')
        operands.forEachIndexed { index, operand ->
            if (index > 0) append(", ")
            append(operand)
        }
        append(')')
    }
}
