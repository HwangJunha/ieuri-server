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
 * 질문 적재(SPEC-0026). 갱신은 관리 엔티티에 얹어 dirty-checking으로 UPDATE를 낸다.
 * - 사진은 생성 시에만 적재한다(질문 수정 없음).
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
        // answerCount는 쓰지 않는다 — 동시 +1을 덮어쓴다. 증감은 SpotQuestionCounterAdapter 소유.
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
     * - 순서 주의: 사진에는 FK가 없어 CASCADE가 닿지 않는다. 질문을 지우기 전에 자식 id를
     *   모아 사진을 먼저 지운다 — 뒤집으면 사진 행이 영구히 남는다.
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

    private fun photoKeysOf(ownerType: SpotQaPhotoOwnerType, ownerId: Long) = photoRepository.findByOwnerTypeAndOwnerIdOrderByPositionAsc(ownerType, ownerId)
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

/** 질문의 답변 수 증감. 원자적 UPDATE로 처리한다([PostCounterAdapter]와 같다). */
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

    /** 0 아래로 내려가지 않는다 — 예외 없이 0에 머문다. */
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

/** 질문·답변·답글의 존재 확인과 자동 숨김. 애그리게이트를 복원하지 않는다. */
@Component
class SpotQaModerationAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQaModerationPort {

    private val question = QSpotQuestionEntity.spotQuestionEntity
    private val answer = QSpotAnswerEntity.spotAnswerEntity
    private val reply = QSpotAnswerReplyEntity.spotAnswerReplyEntity

    @Transactional(readOnly = true)
    override fun questionExists(questionId: Long): Boolean = queryFactory
        .selectOne()
        .from(question)
        .where(question.id.eq(questionId), question.deletedAt.isNull)
        .fetchFirst() != null

    @Transactional(readOnly = true)
    override fun answerExists(answerId: Long): Boolean = queryFactory
        .selectOne()
        .from(answer)
        .where(answer.id.eq(answerId), answer.deletedAt.isNull)
        .fetchFirst() != null

    @Transactional(readOnly = true)
    override fun replyExists(replyId: Long): Boolean = queryFactory
        .selectOne()
        .from(reply)
        .where(reply.id.eq(replyId), reply.deletedAt.isNull)
        .fetchFirst() != null

    /** 이번 호출로 숨겨졌을 때만 true. 이미 숨겨져 있으면 false다. */
    @Transactional
    override fun hideQuestion(questionId: Long): Boolean = queryFactory
        .update(question)
        .set(question.hidden, true)
        .where(question.id.eq(questionId), question.deletedAt.isNull, question.hidden.isFalse)
        .execute() > 0

    /**
     * 답변을 숨기고 그 답변이 달린 질문 id를 돌려준다(호출자가 answerCount를 줄인다).
     * - 이미 숨겨져 있었으면 null이다.
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
    override fun hideReply(replyId: Long): Boolean = queryFactory
        .update(reply)
        .set(reply.hidden, true)
        .where(reply.id.eq(replyId), reply.deletedAt.isNull, reply.hidden.isFalse)
        .execute() > 0
}
