package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.persistence.MemberContentSoftDeletePort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QCommentEntity
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
    private val question = QSpotQuestionEntity.spotQuestionEntity
    private val answer = QSpotAnswerEntity.spotAnswerEntity
    private val reply = QSpotAnswerReplyEntity.spotAnswerReplyEntity

    @Transactional
    override fun softDeletePostsByAuthor(memberId: Long, now: Instant): Long =
        queryFactory.update(post)
            .set(post.deletedAt, now)
            .where(post.authorId.eq(memberId), post.deletedAt.isNull)
            .execute()

    @Transactional
    override fun softDeleteCommentsByAuthor(memberId: Long, now: Instant): Long =
        queryFactory.update(comment)
            .set(comment.deletedAt, now)
            .where(comment.authorId.eq(memberId), comment.deletedAt.isNull)
            .execute()

    @Transactional
    override fun softDeleteSpotQuestionsByAuthor(memberId: Long, now: Instant): Long =
        queryFactory.update(question)
            .set(question.deletedAt, now)
            .where(question.authorId.eq(memberId), question.deletedAt.isNull)
            .execute()

    @Transactional
    override fun softDeleteSpotAnswersByAuthor(memberId: Long, now: Instant): Long =
        queryFactory.update(answer)
            .set(answer.deletedAt, now)
            .where(answer.authorId.eq(memberId), answer.deletedAt.isNull)
            .execute()

    @Transactional
    override fun softDeleteSpotAnswerRepliesByAuthor(memberId: Long, now: Instant): Long =
        queryFactory.update(reply)
            .set(reply.deletedAt, now)
            .where(reply.authorId.eq(memberId), reply.deletedAt.isNull)
            .execute()
}
