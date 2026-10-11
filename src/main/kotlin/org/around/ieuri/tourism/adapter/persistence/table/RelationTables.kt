package org.around.ieuri.tourism.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object MapRelationTable : Table("tour.map_relation") {
    val id = long("id").autoIncrement()
    val ownerId = long("owner_id")
    val counterpartId = long("counterpart_id").index("idx_map_relation_counterpart")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_map_relation", ownerId, counterpartId)
        index("idx_map_relation_owner_created", false, ownerId, createdAt, counterpartId)
        check("ck_map_relation_not_self") { ownerId neq counterpartId }
    }
}
