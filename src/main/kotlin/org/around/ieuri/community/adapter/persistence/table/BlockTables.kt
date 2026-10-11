package org.around.ieuri.community.adapter.persistence.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone

object BlockTable : Table("community.community_block") {
    val id = long("id").autoIncrement()
    val blockerId = long("blocker_id")
    val blockedId = long("blocked_id")
    val createdAt = timestampWithTimeZone("created_at").databaseGenerated()
    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_community_block", blockerId, blockedId)
        index("idx_community_block_blocked", false, blockedId, blockerId)
        index("idx_community_block_blocker", false, blockerId, createdAt, id)
        check("ck_community_block_not_self") { blockerId neq blockedId }
    }
}
