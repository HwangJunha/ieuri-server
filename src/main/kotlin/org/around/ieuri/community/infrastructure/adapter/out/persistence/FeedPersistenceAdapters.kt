package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.security.AesGcmCipher
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.application.port.out.persistence.ConversationRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemCounterPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemLikeRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemModerationPort
import org.around.ieuri.community.application.port.out.persistence.FeedItemRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessagePurgePort
import org.around.ieuri.community.application.port.out.persistence.MessageReportRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.MessageRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.PhotoKeyAlreadyUsed
import org.around.ieuri.community.domain.aggregate.Conversation
import org.around.ieuri.community.domain.aggregate.FeedItem
import org.around.ieuri.community.domain.aggregate.Message
import org.around.ieuri.community.domain.aggregate.MessageReport
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.FeedItemLikeEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageReportItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QFeedItemEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QConversationEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.ConversationJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.FeedItemJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.FeedItemLikeJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.FeedItemPhotoJpaRepository
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
 * 피드 적재. 피드를 먼저 저장해 id를 받은 뒤 사진을 붙인다 — 사진이 피드의 id를 필요로 한다.
 *
 * **사진 키의 재사용을 유일 제약이 판정한다**(AC3-3). 미리 조회해 확인하면 경합에서 두 피드가
 * 같은 키를 나눠 갖는다.
 */
@Component
class FeedItemPersistenceAdapter(
    private val repository: FeedItemJpaRepository,
    private val photoRepository: FeedItemPhotoJpaRepository,
) : FeedItemRepositoryPort {

    override fun findById(feedItemId: Long): FeedItem? {
        val entity = repository.findByIdAndDeletedAtIsNull(feedItemId) ?: return null
        return entity.toDomain(
            photoKeys = photoRepository.findByFeedItemIdOrderByPositionAsc(feedItemId)
                .map { it.toPhotoKey() },
        )
    }

    /**
     * **생성 전용이다.** 피드 수정은 비범위이고, 좋아요 계수와 숨김은 각각 원자적 UPDATE
     * ([FeedItemCounterAdapter]·[FeedItemModerationAdapter])가 처리한다.
     *
     * 갱신 분기를 두지 않는 이유는 **사진이 조용히 사라지기 때문이다** — 갱신 경로는
     * photoKeys를 반영할 수 없는데(전량 교체는 position 유일성 때문에 flush 순서가 얽힌다)
     * 반환값은 사진을 그대로 든 도메인 객체라, 저장되지 않은 것이 저장된 것처럼 보인다.
     * 수정이 필요해지면 그때 사진 교체까지 함께 구현한다.
     */
    override fun save(feedItem: FeedItem): FeedItem {
        require(feedItem.id == null) {
            "피드 수정은 지원하지 않는다. 계수·숨김은 전용 어댑터가 처리한다."
        }
        return create(feedItem)
    }

    private fun create(feedItem: FeedItem): FeedItem {
        val saved = repository.save(feedItem.toNewEntity())
        val feedItemId = requireNotNull(saved.id) { "저장된 피드에는 id가 있어야 한다." }
        try {
            photoRepository.saveAll(feedItem.toPhotoEntities(feedItemId))
            // **flush해야 유일 제약 위반이 여기서 잡힌다** — 커밋 시점으로 미루면 이 try
            // 바깥에서 터져 PHOTO_NOT_CONFIRMED로 옮길 수 없다.
            photoRepository.flush()
        } catch (e: DataIntegrityViolationException) {
            // 이미 다른 피드에 실린 키다. 애플리케이션이 사용자 코드로 옮긴다.
            throw PhotoKeyAlreadyUsed()
        }
        return saved.toDomain(photoKeys = feedItem.photoKeys)
    }
}

/**
 * 피드의 파생 계수 증감. 읽고 고쳐 쓰지 않는다 — 이유는 [PostCounterAdapter]와 같다.
 */
@Component
class FeedItemCounterAdapter(
    private val queryFactory: JPAQueryFactory,
) : FeedItemCounterPort {

    private val feedItem = QFeedItemEntity.feedItemEntity

    @Transactional
    override fun increaseLikeCount(feedItemId: Long) {
        queryFactory
            .update(feedItem)
            .set(feedItem.likeCount, feedItem.likeCount.add(1))
            .where(feedItem.id.eq(feedItemId), feedItem.deletedAt.isNull)
            .execute()
    }

    /** **0 아래로 내려가지 않는다** — 조건절이 0인 행을 아예 건드리지 않는다(AC6-3). */
    @Transactional
    override fun decreaseLikeCount(feedItemId: Long) {
        queryFactory
            .update(feedItem)
            .set(feedItem.likeCount, feedItem.likeCount.subtract(1))
            .where(
                feedItem.id.eq(feedItemId),
                feedItem.deletedAt.isNull,
                feedItem.likeCount.gt(0),
            )
            .execute()
    }
}

@Component
class FeedItemModerationAdapter(
    private val repository: FeedItemJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : FeedItemModerationPort {

    private val feedItem = QFeedItemEntity.feedItemEntity

    @Transactional(readOnly = true)
    override fun exists(feedItemId: Long): Boolean =
        repository.existsByIdAndDeletedAtIsNull(feedItemId)

    /** 이미 숨겨져 있으면 0건이 갱신되고 false다 — 호출자가 중복 처리를 건너뛴다. */
    @Transactional
    override fun hide(feedItemId: Long): Boolean =
        queryFactory
            .update(feedItem)
            .set(feedItem.hidden, true)
            .where(
                feedItem.id.eq(feedItemId),
                feedItem.deletedAt.isNull,
                feedItem.hidden.isFalse,
            )
            .execute() > 0
}

/** 좋아요 유일성. 멱등 판정을 저장소가 맡는다(PostLike와 같은 구조). */
@Component
class FeedItemLikePersistenceAdapter(
    private val repository: FeedItemLikeJpaRepository,
) : FeedItemLikeRepositoryPort {

    @Transactional
    override fun save(feedItemId: Long, memberId: Long): Boolean {
        if (repository.existsByFeedItemIdAndMemberId(feedItemId, memberId)) return false
        return try {
            repository.save(FeedItemLikeEntity(feedItemId = feedItemId, memberId = memberId))
            true
        } catch (e: DataIntegrityViolationException) {
            // 확인과 저장 사이에 다른 요청이 먼저 넣었다 — 멱등이므로 실패가 아니다.
            false
        }
    }

    @Transactional
    override fun delete(feedItemId: Long, memberId: Long): Boolean =
        repository.deleteByFeedItemIdAndMemberId(feedItemId, memberId) > 0
}

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
        val saved = repository.saveAndFlush(message.toNewEntity(cipher.encrypt(message.body)))
        return saved.toDomain(decryptedBody = message.body)
    }

    @Transactional(readOnly = true)
    override fun findAllByIds(messageIds: List<Long>): List<Message> {
        if (messageIds.isEmpty()) return emptyList()
        return repository.findByIdIn(messageIds).map { it.toDomain(cipher.decrypt(it.bodyEncrypted)) }
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
    private val repository: MessageJpaRepository,
    private val objectStoragePort: ObjectStoragePort,
) : MessagePurgePort {

    @Transactional
    override fun deleteCreatedBefore(cutoff: Instant, limit: Int): Int {
        val targets = repository.findByCreatedAtBefore(cutoff, Limit.of(limit))
        if (targets.isEmpty()) return 0

        val deleted = repository.deleteByIdIn(targets.mapNotNull { it.id }).toInt()

        // **첨부 사진도 함께 지운다.** 행만 지우고 오브젝트를 남기면 "6개월 뒤 삭제"가
        // 사진에 대해서는 거짓이 된다 — 보관 기간을 정한 의미가 없어진다.
        //
        // 행 삭제 **뒤에** 지운다. 먼저 지웠다가 트랜잭션이 롤백되면 메시지는 남고 사진만
        // 사라져 깨진 첨부가 된다. 반대 순서에서는 최악이 고아 오브젝트인데, 그쪽이 낫다.
        //
        // 개별 실패를 삼키는 이유는 한 장의 삭제 실패로 회차 전체를 되돌릴 이유가 없어서다 —
        // 스토리지에 이미 없는 키도 정상 경로다(멱등).
        var photoFailures = 0
        targets.mapNotNull { it.photoObjectKey }.forEach { key ->
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
