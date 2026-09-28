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
 * 탈퇴 회원 콘텐츠의 소프트 삭제(SPEC-0011 R17).
 *
 * **물리 삭제하면 남의 화면이 깨진다.** 글을 지우면 거기 달린 남의 댓글이 CASCADE로 함께
 * 사라지고, 질문을 지우면 남이 쓴 답변이 통째로 없어진다. 소프트 삭제는 행을 남기되 읽기
 * 계층이 이미 `deletedAt IS NULL`로 거르고 있어 **표시에서만** 사라진다.
 *
 * **자식을 훑지 않는다.** 종속 관계는 읽기 경로가 이미 강제한다 — 답변 조회는 부모 질문을
 * 먼저 읽어 거르고(`SpotQaQueryService.spotAnswers`), 답글은 답변의 필드라 그 위를 통과해야만
 * 도달한다. 그래서 질문 하나만 칠해도 AC-17.2가 성립하며, 상한 없는 자식 순회가 필요 없다.
 *
 * 벌크 UPDATE는 영속성 컨텍스트를 우회한다. 이 트랜잭션에서 같은 엔티티를 다시 읽는 코드가
 * 없으므로 안전하다(기존 벌크 삭제와 같은 전제다).
 *
 * `@Query` 문자열이 아니라 QueryDSL인 이유는 저장소 규약이다 — 정의가 두 곳으로 갈리고
 * 타입 검사가 되지 않는다.
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
     * `answerCount`를 내려야 할 질문 id(SPEC-0026 R17).
     *
     * **엔티티가 아니라 스칼라를 뽑는다.** 애그리거트를 로드하면 같은 트랜잭션의 영속성
     * 컨텍스트에 managed 사본이 남는데, 바로 뒤의 벌크 UPDATE는 그 컨텍스트를 우회한다 —
     * 나중에 누가 그 엔티티를 건드리면 `deleted_at = NULL`이 되살아나 소프트 삭제가 풀린다.
     *
     * 숨겨진 답변은 이미 수에서 빠져 있고, 이미 지워진 답변도 마찬가지다 — 둘 다 다시 내리면
     * 수가 실제보다 작아진다. **중복은 접지 않는다**: 같은 질문에 여러 건이면 그만큼 내린다.
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
