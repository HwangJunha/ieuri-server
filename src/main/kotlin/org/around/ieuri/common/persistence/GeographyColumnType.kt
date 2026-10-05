package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ColumnType
import org.jetbrains.exposed.v1.core.Table

/**
 * PostGIS `geography` 컬럼을 테이블 선언이 마이그레이션과 같아지도록 선언만 한다. 값은 읽고 쓰지 않는다.
 * - 공간 조회는 raw SQL(`JdbcTemplate`)로 한다 — Exposed DSL은 `ST_*`를 표현하지 않는다.
 * - 값을 채우는 쪽은 DB 트리거라 `databaseGenerated()`와 함께 선언한다.
 */
class GeographyColumnType : ColumnType<String>() {
    override fun sqlType(): String = "geography"

    override fun valueFromDB(value: Any): String = value.toString()
}

fun Table.geography(name: String): Column<String> = registerColumn(name, GeographyColumnType())
