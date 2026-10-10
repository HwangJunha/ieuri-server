package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * PostGIS `geography` 컬럼. 값은 Kotlin으로 읽고 쓰지 않는다 — 공간 연산은 `PostgisExpressions.kt`의 식으로만 다룬다.
 * - 값을 채우는 쪽은 대개 DB 트리거라 `databaseGenerated()`와 함께 선언한다.
 */
class GeographyColumnType : ColumnType<String>() {
    override fun sqlType(): String = "geography"

    override fun valueFromDB(value: Any): String = value.toString()
}

/** PostGIS `geometry` 값의 식 타입. 컬럼으로는 쓰지 않고 `::geometry` 캐스트의 대상으로만 쓴다. */
class GeometryColumnType : ColumnType<String>() {
    override fun sqlType(): String = "geometry"

    override fun valueFromDB(value: Any): String = value.toString()
}

fun Table.geography(name: String): Column<String> = registerColumn(name, GeographyColumnType())
