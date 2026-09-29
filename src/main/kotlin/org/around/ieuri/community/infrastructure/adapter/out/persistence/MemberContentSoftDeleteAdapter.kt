package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.persistence.MemberContentSoftDeletePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QCommentEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QMessageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QPostEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotQuestionEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * 탈퇴 회원 콘텐츠의 소프트 삭제(SPEC-0011 R17). 작성자 본인 행만 칠하고 자식은 훑지 않는다
 * — 종속 숨김은 읽기 경로가 강제한다.
 * - 벌크 UPDATE는 영속성 컨텍스트를 우회한다 — 같은 트랜잭션에서 이 엔티티를 다시 읽지 말 것.
 */
@Component
class MemberContentSoftDeleteAdapter(
    private val queryFactory: JPAQueryFactory,
) : MemberContentSoftDeletePort {

    private val post = QPostEntity.postEntity
    private val comment = QCommentEntity.commentEntity
    private val message = QMessageEntity.messageEntity
    private val question = QSpotQuestionEntity.spotQuestionEntity
    private val answer = QSpotAnswerEntity.spotAnswerEntity
    private val reply = QSpotAnswerReplyEntity.spotAnswerReplyEntity

    @Transactional
    override fun softDeletePostsByAuthor(memberId: Long, now: Instant): Long = queryFactory.update(post)
        .set(post.deletedAt, now)
        .where(post.authorId.eq(memberId), post.deletedAt.isNull)
        .execute()

    @Transactional
    override fun softDeleteCommentsByAuthor(memberId: Long, now: Instant): Long = queryFactory.update(comment)
        .set(comment.deletedAt, now)
        .where(comment.authorId.eq(memberId), comment.deletedAt.isNull)
        .execute()

    @Transactional
    override fun softDeleteSpotQuestionsByAuthor(memberId: Long, now: Instant): Long = queryFactory.update(question)
        .set(question.deletedAt, now)
        .where(question.authorId.eq(memberId), question.deletedAt.isNull)
        .execute()

    @Transactional
    override fun softDeleteSpotAnswersByAuthor(memberId: Long, now: Instant): Long = queryFactory.update(answer)
        .set(answer.deletedAt, now)
        .where(answer.authorId.eq(memberId), answer.deletedAt.isNull)
        .execute()

    @Transactional
    override fun softDeleteSpotAnswerRepliesByAuthor(memberId: Long, now: Instant): Long = queryFactory.update(reply)
        .set(reply.deletedAt, now)
        .where(reply.authorId.eq(memberId), reply.deletedAt.isNull)
        .execute()

    @Transactional
    override fun softDeleteMessagesBySender(memberId: Long, now: Instant): Long = queryFactory.update(message)
        .set(message.deletedAt, now)
        .where(message.senderId.eq(memberId), message.deletedAt.isNull)
        .execute()

    /**
     * `answerCount`를 내려야 할 질문 id(SPEC-0026 R17). 숨겨지거나 지워진 답변은 뺀다.
     * - 중복을 접지 않는다 — 같은 질문에 여러 건이면 그만큼 내린다.
     * - 엔티티가 아니라 스칼라를 뽑는다 — managed 사본이 뒤의 벌크 UPDATE를 되돌릴 수 있다.
     */
    @Transactional(readOnly = true)
    override fun findQuestionIdsToDecrease(memberId: Long): List<Long> = queryFactory
        .select(answer.questionId)
        .from(answer)
        .where(
            answer.authorId.eq(memberId),
            answer.hidden.isFalse,
            answer.deletedAt.isNull,
        )
        .fetch()
}
