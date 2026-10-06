package org.around.ieuri.tourism.adapter.persistence

import org.around.ieuri.tourism.adapter.persistence.mapper.writeTo
import org.around.ieuri.tourism.adapter.persistence.table.SpotReactionTable
import org.around.ieuri.tourism.application.reaction.required.SpotReactionRepository
import org.around.ieuri.tourism.domain.reaction.SpotReaction
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.javatime.CurrentTimestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.upsert
import org.springframework.stereotype.Repository

/**
 * 좋아요/싫어요 저장소 구현.
 * - 저장은 자연키 `(member_id, catalog_id, language)` upsert다 — 첫 반응이 동시에 와도 유일 제약 위반이 나지 않는다.
 * - 반응이 실제로 바뀔 때만 갱신한다 — 같은 반응을 다시 누르면 `updated_at`(좋아요 목록 정렬 키)이 그대로다.
 */
@Repository
class SpotReactionPersistenceAdapter : SpotReactionRepository {

    override fun save(reaction: SpotReaction) {
        SpotReactionTable.upsert(
            SpotReactionTable.memberId,
            SpotReactionTable.catalogId,
            SpotReactionTable.language,
            onUpdate = {
                it[SpotReactionTable.reaction] = reaction.reaction.name
                it[SpotReactionTable.updatedAt] = CurrentTimestampWithTimeZone
            },
            where = { SpotReactionTable.reaction neq reaction.reaction.name },
        ) { reaction.writeTo(it) }
    }

    override fun deleteByMember(memberId: Long): Int = SpotReactionTable.deleteWhere { SpotReactionTable.memberId eq memberId }
}
