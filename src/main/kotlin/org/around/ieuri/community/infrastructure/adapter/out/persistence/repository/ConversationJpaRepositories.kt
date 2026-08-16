package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportItemEntity
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * DM 파생 쿼리(SPEC-0017). @Query 문자열은 두지 않으며 복잡한 조회는 QueryDSL이 맡는다.
 */

interface ConversationJpaRepository : JpaRepository<ConversationEntity, Long> {
    /** 참여자 쌍은 정렬돼 저장되므로 한 방향만 조회하면 된다(AC7-2). */
    fun findByMemberLowAndMemberHigh(memberLow: Long, memberHigh: Long): ConversationEntity?
}

interface MessageJpaRepository : JpaRepository<MessageEntity, Long> {
    fun findByIdIn(ids: List<Long>): List<MessageEntity>

    /** 탈퇴 회원이 보낸 메시지 전량 삭제(R17). 대화 자체는 남긴다(AC17-2). */
    fun deleteBySenderId(senderId: Long): Long

    /**
     * 보관 기간이 지난 메시지를 회차 상한만큼 고른다(R16). **한 번에 다 지우지 않는다** —
     * 6개월치가 쌓인 상태에서 단일 트랜잭션으로 지우면 락과 WAL이 함께 커진다.
     */
    fun findByCreatedAtBefore(cutoff: Instant, limit: Limit): List<MessageEntity>

    /** 고른 id만 지운다. 선별과 삭제를 나눠 회차 상한이 실제로 지켜지게 한다. */
    fun deleteByIdIn(ids: List<Long>): Long
}

interface MessageReportJpaRepository : JpaRepository<MessageReportEntity, Long>

interface MessageReportItemJpaRepository : JpaRepository<MessageReportItemEntity, Long> {
    /** 신고자가 이미 신고한 메시지가 있는지(AC15-6). 중복 판정은 신고자 단위다. */
    fun existsByReporterIdAndMessageIdIn(reporterId: Long, messageIds: List<Long>): Boolean
}
