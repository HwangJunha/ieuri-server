package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.mapper.writeTo
import org.around.ieuri.community.adapter.persistence.table.BlockTable
import org.around.ieuri.community.application.block.required.BlockRepository
import org.around.ieuri.community.domain.block.Block
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.springframework.stereotype.Repository

@Repository
class BlockPersistenceAdapter : BlockRepository {

    override fun add(block: Block): Boolean = BlockTable.insertIgnore { block.writeTo(it) }.insertedCount > 0

    override fun remove(blockerId: Long, blockedId: Long): Boolean = BlockTable.deleteWhere { (BlockTable.blockerId eq blockerId) and (BlockTable.blockedId eq blockedId) } > 0

    override fun removeInvolving(memberId: Long): Int = BlockTable.deleteWhere { (BlockTable.blockerId eq memberId) or (BlockTable.blockedId eq memberId) }
}
