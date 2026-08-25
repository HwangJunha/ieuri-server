package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity
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
    /**
     * 신고 대상 메시지 조회. **지워진 것은 오지 않는다** — 화면에 없는 메시지를 id로만
     * 신고할 수 있으면 안 된다. 예전에는 탈퇴가 행을 지워 자연히 막혔다.
     */
    fun findByIdInAndDeletedAtIsNull(ids: List<Long>): List<MessageEntity>

    // 탈퇴 회원의 메시지는 **소프트 삭제한다**(R17) — 지우면 상대가 대화 맥락을 잃는다.
    // 파생 쿼리로 컬럼을 채울 수 없어 QueryDSL 어댑터가 맡는다(MemberContentSoftDeleteAdapter).

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

/**
 * 메시지 인용. 메시지 목록을 그릴 때 **한 번에** 읽는다 — 메시지마다 물으면 30건짜리
 * 페이지에 조회가 30번 붙는다(N+1).
 *
 * 메시지가 보관 기간으로 지워지면 FK CASCADE가 인용도 함께 지운다.
 */
interface MessageQuoteJpaRepository : JpaRepository<MessageQuoteEntity, Long> {
    fun findByMessageIdIn(messageIds: Collection<Long>): List<MessageQuoteEntity>
}
