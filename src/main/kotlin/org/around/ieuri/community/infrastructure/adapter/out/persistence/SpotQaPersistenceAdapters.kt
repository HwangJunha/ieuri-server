package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerReplyRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotAnswerRepositoryPort
import org.around.ieuri.community.application.port.out.persistence.SpotQaModerationPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionRepositoryPort
import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotQuestionEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toDomain
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toNewEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoEntities
import org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper.toPhotoKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotAnswerJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotAnswerReplyJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotQaPhotoJpaRepository
import org.around.ieuri.community.infrastructure.adapter.out.persistence.repository.SpotQuestionJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 질문 적재(SPEC-0026). 갱신은 **관리 엔티티에 도메인 변경을 얹어** dirty-checking으로
 * UPDATE를 낸다 — 새 엔티티를 만들어 save하면 dirty-checking이 무의미해진다.
 *
 * 사진은 생성 시에만 적재한다 — 질문 수정 기능이 없어(범위 밖: 삭제 후 재작성) 구성이
 * 나중에 바뀌지 않는다.
 */
@Component
class SpotQuestionPersistenceAdapter(
    private val repository: SpotQuestionJpaRepository,
    private val answerRepository: SpotAnswerJpaRepository,
    private val replyRepository: SpotAnswerReplyJpaRepository,
    private val photoRepository: SpotQaPhotoJpaRepository,
) : SpotQuestionRepositoryPort {

    @Transactional(readOnly = true)
    override fun findById(questionId: Long): SpotQuestion? {
        val entity = repository.findByIdAndDeletedAtIsNull(questionId) ?: return null
        return entity.toDomain(photoKeys = photoKeysOf(SpotQaPhotoOwnerType.QUESTION, questionId))
    }

    @Transactional
    override fun save(question: SpotQuestion): SpotQuestion {
        val id = question.id ?: return create(question)

        val entity = repository.findById(id).orElseThrow {
            IllegalStateException("갱신하려는 질문($id)이 존재하지 않는다.")
        }
        entity.resolved = question.resolved
        // **answerCount는 여기서 쓰지 않는다.** 적재와 flush 사이에 답변이 달리면 그 +1을
        // 덮어써 수가 영구히 작아진다 — 증감은 SpotQuestionCounterAdapter의 원자적 UPDATE가
        // 소유하며, 이 경로(해결 표시)는 그 값을 건드리지 않는다.
        entity.hidden = question.hidden
        entity.deletedAt = question.deletedAt
        return question
    }

    private fun create(question: SpotQuestion): SpotQuestion {
        val saved = repository.save(question.toNewEntity())
        val questionId = requireNotNull(saved.id) { "저장된 질문에는 id가 있어야 한다." }
        photoRepository.saveAll(
            question.photoKeys.toPhotoEntities(SpotQaPhotoOwnerType.QUESTION, questionId),
        )
        return saved.toDomain(photoKeys = question.photoKeys)
    }

    /**
     * 질문·답변·답글의 물리 삭제(AC-10.3).
     *
     * **순서가 전부다.** 사진에는 FK가 없어 CASCADE가 닿지 않으므로, 질문을 지우기 **전에**
     * 자식 id를 모아 사진을 먼저 지운다. 순서를 뒤집으면 답변·답글이 사라진 뒤라 그 id를
     * 알 수 없고, 사진 행이 영구히 남는다.
     *
     * 소프트 삭제가 아닌 이유는 종속 삭제 때문이다 — 답변·답글까지 deleted_at을 칠하려면
     * 자식을 전부 훑어야 하는데 그 수에 상한이 없다.
     */
    @Transactional
    override fun deleteWithDescendants(questionId: Long) {
        val answerIds = answerRepository.findByQuestionId(questionId).mapNotNull { it.id }
        val replyIds = if (answerIds.isEmpty()) {
            emptyList()
        } else {
            replyRepository.findByAnswerIdIn(answerIds).mapNotNull { it.id }
        }

        if (replyIds.isNotEmpty()) {
            photoRepository.deleteByOwnerTypeAndOwnerIdIn(SpotQaPhotoOwnerType.REPLY, replyIds)
        }
        if (answerIds.isNotEmpty()) {
            photoRepository.deleteByOwnerTypeAndOwnerIdIn(SpotQaPhotoOwnerType.ANSWER, answerIds)
        }
        photoRepository.deleteByOwnerTypeAndOwnerId(SpotQaPhotoOwnerType.QUESTION, questionId)

        // 답변·답글 행은 FK CASCADE가 따라 지운다.
        repository.deleteById(questionId)
    }

    private fun photoKeysOf(ownerType: SpotQaPhotoOwnerType, ownerId: Long) =
        photoRepository.findByOwnerTypeAndOwnerIdOrderByPositionAsc(ownerType, ownerId)
            .map { it.toPhotoKey() }
}

@Component
class SpotAnswerPersistenceAdapter(
    private val repository: SpotAnswerJpaRepository,
    private val replyRepository: SpotAnswerReplyJpaRepository,
    private val photoRepository: SpotQaPhotoJpaRepository,
) : SpotAnswerRepositoryPort {

    @Transactional(readOnly = true)
    override fun findById(answerId: Long): SpotAnswer? {
        val entity = repository.findByIdAndDeletedAtIsNull(answerId) ?: return null
        return entity.toDomain(
            photoKeys = photoRepository
                .findByOwnerTypeAndOwnerIdOrderByPositionAsc(SpotQaPhotoOwnerType.ANSWER, answerId)
                .map { it.toPhotoKey() },
        )
    }

    @Transactional
    override fun save(answer: SpotAnswer): SpotAnswer {
        val id = answer.id ?: return create(answer)

        val entity = repository.findById(id).orElseThrow {
            IllegalStateException("갱신하려는 답변($id)이 존재하지 않는다.")
        }
        entity.hidden = answer.hidden
        entity.deletedAt = answer.deletedAt
        return answer
    }

    private fun create(answer: SpotAnswer): SpotAnswer {
        val saved = repository.save(answer.toNewEntity())
        val answerId = requireNotNull(saved.id) { "저장된 답변에는 id가 있어야 한다." }
        photoRepository.saveAll(
            answer.photoKeys.toPhotoEntities(SpotQaPhotoOwnerType.ANSWER, answerId),
        )
        return saved.toDomain(photoKeys = answer.photoKeys)
    }

    /** 질문 삭제와 같은 순서 규율 — 사진에 FK가 없어 답글 id를 먼저 모은다(AC-10.4). */
    @Transactional
    override fun deleteWithDescendants(answerId: Long) {
        val replyIds = replyRepository.findByAnswerId(answerId).mapNotNull { it.id }
        if (replyIds.isNotEmpty()) {
            photoRepository.deleteByOwnerTypeAndOwnerIdIn(SpotQaPhotoOwnerType.REPLY, replyIds)
        }
        photoRepository.deleteByOwnerTypeAndOwnerId(SpotQaPhotoOwnerType.ANSWER, answerId)
        repository.deleteById(answerId)
    }
}

@Component
class SpotAnswerReplyPersistenceAdapter(
    private val repository: SpotAnswerReplyJpaRepository,
    private val photoRepository: SpotQaPhotoJpaRepository,
) : SpotAnswerReplyRepositoryPort {

    @Transactional(readOnly = true)
    override fun findById(replyId: Long): SpotAnswerReply? {
        val entity = repository.findByIdAndDeletedAtIsNull(replyId) ?: return null
        return entity.toDomain(
            photoKeys = photoRepository
                .findByOwnerTypeAndOwnerIdOrderByPositionAsc(SpotQaPhotoOwnerType.REPLY, replyId)
                .map { it.toPhotoKey() },
        )
    }

    @Transactional
    override fun save(reply: SpotAnswerReply): SpotAnswerReply {
        val id = reply.id ?: return create(reply)

        val entity = repository.findById(id).orElseThrow {
            IllegalStateException("갱신하려는 답글($id)이 존재하지 않는다.")
        }
        entity.hidden = reply.hidden
        entity.deletedAt = reply.deletedAt
        return reply
    }

    private fun create(reply: SpotAnswerReply): SpotAnswerReply {
        val saved = repository.save(reply.toNewEntity())
        val replyId = requireNotNull(saved.id) { "저장된 답글에는 id가 있어야 한다." }
        photoRepository.saveAll(reply.photoKeys.toPhotoEntities(SpotQaPhotoOwnerType.REPLY, replyId))
        return saved.toDomain(photoKeys = reply.photoKeys)
    }

    @Transactional
    override fun delete(replyId: Long) {
        photoRepository.deleteByOwnerTypeAndOwnerId(SpotQaPhotoOwnerType.REPLY, replyId)
        repository.deleteById(replyId)
    }
}

/**
 * 질문의 답변 수 증감. **읽고 고쳐 쓰지 않는다** — 동시 답변이 서로의 증가를 덮어써 계수가
 * 실제보다 작아지기 때문이다. [PostCounterAdapter]와 같은 규율이다.
 */
@Component
class SpotQuestionCounterAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQuestionCounterPort {

    private val question = QSpotQuestionEntity.spotQuestionEntity

    @Transactional
    override fun increaseAnswerCount(questionId: Long) {
        queryFactory
            .update(question)
            .set(question.answerCount, question.answerCount.add(1))
            .where(question.id.eq(questionId), question.deletedAt.isNull)
            .execute()
    }

    /**
     * **0 아래로 내려가지 않는다.** 조건절이 0인 행을 아예 건드리지 않으므로 CHECK 제약도
     * 걸리지 않는다 — 음수를 쓰려다 예외가 나는 대신 조용히 0으로 머문다.
     */
    @Transactional
    override fun decreaseAnswerCount(questionId: Long) {
        queryFactory
            .update(question)
            .set(question.answerCount, question.answerCount.subtract(1))
            .where(
                question.id.eq(questionId),
                question.deletedAt.isNull,
                question.answerCount.gt(0),
            )
            .execute()
    }
}

/**
 * 존재 확인과 자동 숨김. 애그리게이트를 복원하지 않는다 — 신고의 대상 확인은 "있는가"만
 * 물으면 되는데 복원하면 사진까지 읽는다.
 */
@Component
class SpotQaModerationAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQaModerationPort {

    private val question = QSpotQuestionEntity.spotQuestionEntity
    private val answer = QSpotAnswerEntity.spotAnswerEntity
    private val reply = QSpotAnswerReplyEntity.spotAnswerReplyEntity

    @Transactional(readOnly = true)
    override fun questionExists(questionId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(question)
            .where(question.id.eq(questionId), question.deletedAt.isNull)
            .fetchFirst() != null

    @Transactional(readOnly = true)
    override fun answerExists(answerId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(answer)
            .where(answer.id.eq(answerId), answer.deletedAt.isNull)
            .fetchFirst() != null

    @Transactional(readOnly = true)
    override fun replyExists(replyId: Long): Boolean =
        queryFactory
            .selectOne()
            .from(reply)
            .where(reply.id.eq(replyId), reply.deletedAt.isNull)
            .fetchFirst() != null

    /** 이미 숨겨져 있으면 조건절에서 걸러져 0건이 갱신되고 false다 — 중복 처리를 막는다. */
    @Transactional
    override fun hideQuestion(questionId: Long): Boolean =
        queryFactory
            .update(question)
            .set(question.hidden, true)
            .where(question.id.eq(questionId), question.deletedAt.isNull, question.hidden.isFalse)
            .execute() > 0

    /**
     * 답변을 숨기고 **그 답변이 달린 질문 id를 돌려준다** — 호출자가 answerCount를 줄여야
     * 하기 때문이다. 갱신 전에 읽는 이유는 벌크 update가 갱신된 행을 돌려주지 않아서다.
     *
     * 이미 숨겨져 있었으면 0건이 갱신되고 null이라, 신고가 더 쌓여도 답변 수를 두 번
     * 내리는 일이 없다.
     */
    @Transactional
    override fun hideAnswer(answerId: Long): Long? {
        val questionId = queryFactory
            .select(answer.questionId)
            .from(answer)
            .where(answer.id.eq(answerId), answer.deletedAt.isNull, answer.hidden.isFalse)
            .fetchFirst()
            ?: return null

        val updated = queryFactory
            .update(answer)
            .set(answer.hidden, true)
            .where(answer.id.eq(answerId), answer.deletedAt.isNull, answer.hidden.isFalse)
            .execute()
        return if (updated > 0) questionId else null
    }

    @Transactional
    override fun hideReply(replyId: Long): Boolean =
        queryFactory
            .update(reply)
            .set(reply.hidden, true)
            .where(reply.id.eq(replyId), reply.deletedAt.isNull, reply.hidden.isFalse)
            .execute() > 0
}
