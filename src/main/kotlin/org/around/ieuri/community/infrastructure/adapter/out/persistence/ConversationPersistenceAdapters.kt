package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.ExpiredMessagePurgeResult
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.application.port.out.persistence.MessageReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toQuoteEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toSpotQaOrigin
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ConversationJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageQuoteJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageReportItemJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageReportJpaRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** 대화 적재. [saveIfAbsent]는 경합으로 유일 제약에 걸리면 기존 대화를 돌려준다(AC7-2). */
@Component
class ConversationPersistenceAdapter(
    private val repository: ConversationJpaRepository,
) : ConversationRepositoryPort {

    override fun findById(conversationId: Long): Conversation? = repository.findById(conversationId).orElse(null)?.toDomain()

    @Transactional
    override fun saveIfAbsent(conversation: Conversation): Conversation {
        repository.findByMemberLowAndMemberHigh(conversation.memberLow, conversation.memberHigh)
            ?.let { return it.toDomain() }

        return try {
            repository.saveAndFlush(conversation.toNewEntity()).toDomain()
        } catch (e: DataIntegrityViolationException) {
            val existing = repository.findByMemberLowAndMemberHigh(
                conversation.memberLow,
                conversation.memberHigh,
            )
            requireNotNull(existing) { "유일 제약에 걸렸는데 기존 대화를 찾을 수 없다." }.toDomain()
        }
    }

    /** 최근 활동 시각 갱신. */
    @Transactional
    override fun touch(conversationId: Long, at: Instant) {
        repository.findById(conversationId).orElse(null)?.lastMessageAt = at
    }

    /** 읽음 지점 갱신. 읽음 컬럼만 관리 엔티티에 얹는다(다른 컬럼을 덮어쓰지 않게). */
    @Transactional
    override fun updateReadState(conversation: Conversation) {
        val id = requireNotNull(conversation.id) { "저장된 대화만 읽음 지점을 갱신할 수 있다." }
        val entity = repository.findById(id).orElse(null) ?: return
        entity.lastReadMessageIdLow = conversation.lastReadMessageIdLow
        entity.lastReadMessageIdHigh = conversation.lastReadMessageIdHigh
    }
}

/**
 * 메시지 적재. 본문·인용 발췌의 암복호화를 여기서 한다(R16).
 * - 인용은 메시지를 먼저 저장해 id를 받은 뒤 붙인다.
 */
@Component
class MessagePersistenceAdapter(
    private val repository: MessageJpaRepository,
    private val quoteRepository: MessageQuoteJpaRepository,
    private val cipher: AesGcmCipher,
) : MessageRepositoryPort {

    @Transactional
    override fun save(message: Message): Message {
        val saved = repository.saveAndFlush(message.toNewEntity(cipher.encrypt(message.body)))
        val messageId = requireNotNull(saved.id) { "저장된 메시지에는 id가 있어야 한다." }

        message.spotQaOrigin?.let { origin ->
            quoteRepository.save(
                origin.toQuoteEntity(
                    messageId = messageId,
                    encryptedQuestionExcerpt = cipher.encrypt(origin.questionExcerpt),
                    encryptedAnswerExcerpt = cipher.encrypt(origin.answerExcerpt),
                ),
            )
        }
        return saved.toDomain(decryptedBody = message.body, spotQaOrigin = message.spotQaOrigin)
    }

    /** 지워지지 않은 메시지만 돌려준다. 인용은 한 번에 읽는다. */
    @Transactional(readOnly = true)
    override fun findAllByIds(messageIds: List<Long>): List<Message> {
        if (messageIds.isEmpty()) return emptyList()

        val quotes = quoteRepository.findByMessageIdIn(messageIds).associate { quote ->
            quote.messageId to quote.toSpotQaOrigin(
                decryptedPrimaryExcerpt = cipher.decrypt(quote.primaryExcerptEncrypted),
                decryptedSecondaryExcerpt = quote.secondaryExcerptEncrypted
                    ?.let { cipher.decrypt(it) }
                    .orEmpty(),
            )
        }
        return repository.findByIdInAndDeletedAtIsNull(messageIds).map { entity ->
            entity.toDomain(
                decryptedBody = cipher.decrypt(entity.bodyEncrypted),
                spotQaOrigin = quotes[entity.id],
            )
        }
    }
}

/** DM 신고 저장. 중복 판정은 신고자 단위의 유일 제약이 맡는다(AC15-6). */
@Component
class MessageReportPersistenceAdapter(
    private val reportRepository: MessageReportJpaRepository,
    private val itemRepository: MessageReportItemJpaRepository,
) : MessageReportRepositoryPort {

    @Transactional
    override fun save(report: MessageReport): Boolean {
        if (itemRepository.existsByReporterIdAndMessageIdIn(report.reporterId, report.messageIds)) {
            return false
        }
        val saved = reportRepository.save(report.toNewEntity())
        val reportId = requireNotNull(saved.id) { "저장된 신고에는 id가 있어야 한다." }
        return try {
            itemRepository.saveAll(
                report.messageIds.map {
                    MessageReportItemEntity(
                        reportId = reportId,
                        messageId = it,
                        reporterId = report.reporterId,
                    )
                },
            )
            itemRepository.flush()
            true
        } catch (e: DataIntegrityViolationException) {
            // 동시에 같은 신고자가 먼저 넣었다 — 중복이므로 접수하지 않는다.
            false
        }
    }
}

/**
 * 만료 메시지 행의 물리 삭제(R16). 회차당 최대 [limit]건.
 * - 첨부 오브젝트는 지우지 않는다 — 키만 돌려주고 호출자가 트랜잭션 밖에서 지운다.
 */
@Component
class ExpiredMessageRowPurger(
    private val repository: MessageJpaRepository,
) : MessagePurgePort {

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): ExpiredMessagePurgeResult {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return ExpiredMessagePurgeResult(deletedCount = 0, photoObjectKeys = emptyList())

        // 키는 행 삭제 전에 모아야 한다.
        val photoKeys = targets.mapNotNull { it.photoObjectKey }
        val deleted = repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()
        return ExpiredMessagePurgeResult(deletedCount = deleted, photoObjectKeys = photoKeys)
    }
}
