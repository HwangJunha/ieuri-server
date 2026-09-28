package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportItemEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ConversationJpaRepository : JpaRepository<ConversationEntity, Long> {
    /** 참여자 쌍은 정렬돼 있어야 한다(low < high). */
    fun findByMemberLowAndMemberHigh(memberLow: Long, memberHigh: Long): ConversationEntity?
}

interface MessageJpaRepository : JpaRepository<MessageEntity, Long> {
    /** 지워진 메시지는 오지 않는다. */
    fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<MessageEntity>

    /** 보관 기간이 지난 메시지를 회차 상한만큼 고른다(R16). */
    fun findByCreatedAtBefore(cutoff: Instant, limit: Limit): List<MessageEntity>

    fun deleteByIdIn(ids: List<Long>): Long
}

interface MessageReportJpaRepository : JpaRepository<MessageReportEntity, Long>

interface MessageReportItemJpaRepository : JpaRepository<MessageReportItemEntity, Long> {
    /** 신고자가 이미 신고한 메시지가 있는지(AC15-6). */
    fun existsByReporterIdAndMessageIdIn(reporterId: Long, messageIds: List<Long>): Boolean
}

/** 메시지 인용. 메시지 행이 지워지면 FK CASCADE가 인용도 지운다. */
interface MessageQuoteJpaRepository : JpaRepository<MessageQuoteEntity, Long> {
    fun findByMessageIdIn(messageIds: Collection<Long>): List<MessageQuoteEntity>
}
