package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
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
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ConversationJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageReportItemJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.MessageReportJpaRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 대화 적재. [saveIfAbsent]가 **경합을 흡수한다** — 유일 제약에 걸리면 새로 만드는 대신
 * 이미 만들어진 것을 돌려준다(AC7-2).
 */
@Component
class ConversationPersistenceAdapter(
    private val repository: ConversationJpaRepository,
) : ConversationRepositoryPort {

    override fun findById(conversationId: Long): Conversation? =
        repository.findById(conversationId).orElse(null)?.toDomain()

    @Transactional
    override fun saveIfAbsent(conversation: Conversation): Conversation {
        repository.findByMemberLowAndMemberHigh(conversation.memberLow, conversation.memberHigh)
            ?.let { return it.toDomain() }

        return try {
            repository.saveAndFlush(conversation.toNewEntity()).toDomain()
        } catch (e: DataIntegrityViolationException) {
            // 조회와 저장 사이에 다른 요청이 먼저 만들었다. 새로 만들지 않고 그것을 쓴다.
            val existing = repository.findByMemberLowAndMemberHigh(
                conversation.memberLow,
                conversation.memberHigh,
            )
            requireNotNull(existing) { "유일 제약에 걸렸는데 기존 대화를 찾을 수 없다." }.toDomain()
        }
    }

    /** 최근 활동 시각 갱신. 관리 엔티티에 얹어 dirty-checking으로 UPDATE를 낸다. */
    @Transactional
    override fun touch(conversationId: Long, at: Instant) {
        repository.findById(conversationId).orElse(null)?.lastMessageAt = at
    }

    /**
     * 읽음 지점 갱신. **관리 엔티티에 얹는다** — 도메인을 새 엔티티로 만들어 save()하면
     * lastMessageAt 등 이 트랜잭션이 건드리지 않은 컬럼까지 덮어쓴다.
     */
    @Transactional
    override fun updateReadState(conversation: Conversation) {
        val id = requireNotNull(conversation.id) { "저장된 대화만 읽음 지점을 갱신할 수 있다." }
        val entity = repository.findById(id).orElse(null) ?: return
        entity.lastReadMessageIdLow = conversation.lastReadMessageIdLow
        entity.lastReadMessageIdHigh = conversation.lastReadMessageIdHigh
    }
}

/**
 * 메시지 적재. **본문 암복호화를 여기서 한다**(R16) — 키를 아는 것은 어댑터이고, 매퍼는
 * 암호문·평문을 파라미터로 받는 순수 함수로 남는다(POL-033).
 */
@Component
class MessagePersistenceAdapter(
    private val repository: MessageJpaRepository,
    private val cipher: AesGcmCipher,
) : MessageRepositoryPort {

    @Transactional
    override fun save(message: Message): Message {
        val origin = message.spotQaOrigin
        val saved = repository.saveAndFlush(
            message.toNewEntity(
                encryptedBody = cipher.encrypt(message.body),
                encryptedQuestionExcerpt = origin?.let { cipher.encrypt(it.questionExcerpt) },
                encryptedAnswerExcerpt = origin?.let { cipher.encrypt(it.answerExcerpt) },
            ),
        )
        // 인자의 평문을 그대로 쓴다 — 방금 손에 쥔 값을 다시 복호화할 이유가 없다.
        return saved.toDomain(
            decryptedBody = message.body,
            decryptedQuestionExcerpt = origin?.questionExcerpt,
            decryptedAnswerExcerpt = origin?.answerExcerpt,
        )
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(messageIds: List<Long>): List<Message> {
        if (messageIds.isEmpty()) return emptyList()
        return repository.findByIdIn(messageIds).map { entity ->
            entity.toDomain(
                decryptedBody = cipher.decrypt(entity.bodyEncrypted),
                decryptedQuestionExcerpt = entity.spotQaQuestionBodyEncrypted?.let { cipher.decrypt(it) },
                decryptedAnswerExcerpt = entity.spotQaAnswerBodyEncrypted?.let { cipher.decrypt(it) },
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
            // 확인과 저장 사이에 같은 신고자가 먼저 넣었다 — 중복이므로 접수하지 않는다.
            false
        }
    }
}

/**
 * 보관 기간 만료·탈퇴 메시지 삭제. **물리 삭제다**(R16·R17).
 *
 * 선별과 삭제를 나누는 이유는 회차 상한을 실제로 지키기 위해서다 — 조건 하나로 지우면
 * 6개월치가 한 트랜잭션에 들어가 락과 WAL이 함께 커진다.
 */
@Component
class MessagePurgeAdapter(
    private val rowPurger: ExpiredMessageRowPurger,
    private val objectStoragePort: ObjectStoragePort,
) : MessagePurgePort {

    /**
     * **오브젝트 삭제는 트랜잭션 밖이다.** 회차당 최대 limit건의 R2 왕복이 트랜잭션 안에 있으면
     * DB 커넥션 보유 시간이 원격 레이턴시에 묶여, 1000건이면 한 커넥션을 수십 초 잡는다.
     */
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int {
        val (deleted, photoKeys) = rowPurger.deleteRows(cutoff, limit)
        if (deleted == 0) return 0

        // **첨부 사진도 함께 지운다.** 행만 지우고 오브젝트를 남기면 "6개월 뒤 삭제"가
        // 사진에 대해서는 거짓이 된다 — 보관 기간을 정한 의미가 없어진다.
        //
        // 행 삭제 **뒤에** 지운다. 먼저 지웠다가 트랜잭션이 롤백되면 메시지는 남고 사진만
        // 사라져 깨진 첨부가 된다. 반대 순서에서는 최악이 고아 오브젝트인데, 그쪽이 낫다.
        //
        // 개별 실패를 삼키는 이유는 한 장의 삭제 실패로 회차 전체를 되돌릴 이유가 없어서다 —
        // 스토리지에 이미 없는 키도 정상 경로다(멱등).
        var photoFailures = 0
        photoKeys.forEach { key ->
            runCatching { objectStoragePort.delete(key) }.onFailure { photoFailures += 1 }
        }
        // 키에 memberId 구간이 있어 로그에 싣지 않는다(POL-013) — 건수만 남긴다.
        if (photoFailures > 0) log.warn("failed to delete {} expired DM photo objects", photoFailures)
        return deleted
    }

    private companion object {
        private val log = LoggerFactory.getLogger(MessagePurgeAdapter::class.java)
    }
}

/**
 * 만료 메시지의 **행 삭제만** 담당한다.
 *
 * [MessagePurgeAdapter]와 별도 빈인 이유는 프록시 때문이다 — 같은 클래스 안의 메서드를
 * 자기호출하면 프록시를 거치지 않아 `@Transactional`이 조용히 무시된다. 트랜잭션을 원격
 * 호출에서 떼어내려면 경계가 빈 밖에 있어야 한다.
 */
@Component
class ExpiredMessageRowPurger(
    private val repository: MessageJpaRepository,
) {
    /** 삭제 건수와, 뒤이어 스토리지에서 지워야 할 오브젝트 키를 함께 돌려준다. */
    @Transactional
    fun deleteRows(cutoff: Instant, limit: Int): Pair<Int, List<String>> {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return 0 to emptyList()

        val photoKeys = targets.mapNotNull { it.photoObjectKey }
        val deleted = repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()
        return deleted to photoKeys
    }
}
