package org.around.ieuri.community.infrastructure.adapter.out.query

import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.core.types.dsl.BooleanPath
import com.querydsl.core.types.dsl.DateTimePath
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.core.types.dsl.NumberPath
import com.querydsl.jpa.JPAExpressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.common.pagination.cursorPageOf
import org.around.ieuri.community.application.port.out.query.SpotAnswersSearch
import org.around.ieuri.community.application.port.out.query.SpotQaOriginSource
import org.around.ieuri.community.application.port.out.query.SpotQaQueryPort
import org.around.ieuri.community.application.port.out.query.SpotQuestionsSearch
import org.around.ieuri.community.application.port.out.query.ViewerContext
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotCatalogLanguageEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotQaPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotQuestionEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQuestionEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 장소 질문/답변 읽기(SPEC-0026). 차단·숨김 필터는 커뮤니티 글과 같은 모양이다. */
@Component
class SpotQaQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQaQueryPort {

    private val question = QSpotQuestionEntity.spotQuestionEntity
    private val answer = QSpotAnswerEntity.spotAnswerEntity
    private val reply = QSpotAnswerReplyEntity.spotAnswerReplyEntity
    private val photo = QSpotQaPhotoEntity.spotQaPhotoEntity
    private val catalogLanguage = QSpotCatalogLanguageEntity.spotCatalogLanguageEntity

    /** 질문 목록. 정렬은 미해결 우선, 그 안에서 최신순으로 고정이다(`resolved ASC` = false 먼저). */
    @Transactional(readOnly = true)
    override fun findQuestions(search: SpotQuestionsSearch): CursorPage<SpotQuestionView> {
        val cursor = search.after?.let { decodeSpotQuestionCursor(it) }

        val rows = queryFactory
            .selectFrom(question)
            .where(
                search.catalogId?.let { question.catalogId.eq(it) },
                search.authorId?.let { question.authorId.eq(it) },
                idsCondition(search.ids),
                languageCondition(search.language),
                visibleToViewer(search.viewer),
                cursor?.let { questionKeysetCondition(it) },
            )
            .orderBy(question.resolved.asc(), question.createdAt.desc(), question.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            CursorEdge(
                cursor = encodeSpotQuestionCursor(
                    resolved = entity.resolved,
                    createdAt = requireNotNull(entity.createdAt),
                    id = requireNotNull(entity.id),
                ),
                node = entity.toQuestionView(search.viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    @Transactional(readOnly = true)
    override fun findQuestion(
        questionId: Long,
        viewer: ViewerContext,
        includeHidden: Boolean,
    ): SpotQuestionView? = queryFactory
        .selectFrom(question)
        .where(
            question.id.eq(questionId),
            question.deletedAt.isNull,
            // 차단은 언제나 건다. 숨김만 선택적으로 우회한다(R11 종속 판정용).
            blockExclusion(viewer, question.authorId),
            if (includeHidden) null else hiddenExclusion(viewer, question.hidden, question.authorId),
        )
        .fetchOne()
        ?.toQuestionView(viewer)

    /**
     * 답변 목록. 최신순.
     * - 질문의 숨김 종속(R11)은 여기서 보지 않는다 — 호출자가 질문을 먼저 판정한 뒤 부른다.
     */
    @Transactional(readOnly = true)
    override fun findAnswers(search: SpotAnswersSearch): CursorPage<SpotAnswerView> {
        val cursor = search.after?.let { decodeCursor(it) }

        val rows = queryFactory
            .selectFrom(answer)
            .where(
                answer.questionId.eq(search.questionId),
                answer.deletedAt.isNull,
                blockExclusion(search.viewer, answer.authorId),
                hiddenExclusion(search.viewer, answer.hidden, answer.authorId),
                cursor?.let { keysetCondition(answer.createdAt, answer.id, it) },
            )
            .orderBy(answer.createdAt.desc(), answer.id.desc())
            .limit((search.first + 1).toLong())
            .fetch()

        val hasNextPage = rows.size > search.first
        val pageRows = if (hasNextPage) rows.take(search.first) else rows
        val edges = pageRows.map { entity ->
            CursorEdge(
                cursor = encodeCursor(requireNotNull(entity.createdAt), requireNotNull(entity.id)),
                node = entity.toAnswerView(search.viewer),
            )
        }
        return cursorPageOf(edges, hasNextPage = hasNextPage, hasPreviousPage = cursor != null)
    }

    /**
     * 여러 답변의 답글을 한 번에 읽는다(DataLoader용).
     * - 답글은 오래된 순이며 페이지네이션이 없다.
     */
    @Transactional(readOnly = true)
    override fun findRepliesOf(
        answerIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<SpotAnswerReplyView>> {
        if (answerIds.isEmpty()) return emptyMap()

        return queryFactory
            .selectFrom(reply)
            .where(
                reply.answerId.`in`(answerIds),
                reply.deletedAt.isNull,
                blockExclusion(viewer, reply.authorId),
                hiddenExclusion(viewer, reply.hidden, reply.authorId),
            )
            .orderBy(reply.createdAt.asc(), reply.id.asc())
            .fetch()
            .groupBy({ it.answerId }, { it.toReplyView(viewer) })
    }

    @Transactional(readOnly = true)
    override fun findQuestionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwnerType.QUESTION, questionIds)

    @Transactional(readOnly = true)
    override fun findAnswerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwnerType.ANSWER, answerIds)

    @Transactional(readOnly = true)
    override fun findReplyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwnerType.REPLY, replyIds)

    /**
     * 출처 스냅샷의 재료. 답변과 질문을 한 번의 조인으로 읽는다.
     * - 답변이나 질문이 삭제됐으면 null. 숨김 여부는 보지 않는다.
     */
    @Transactional(readOnly = true)
    override fun findAnswerOriginSource(answerId: Long): SpotQaOriginSource? = queryFactory
        .select(
            answer.catalogId,
            question.id,
            question.body,
            answer.id,
            answer.body,
            answer.authorId,
        )
        .from(answer)
        .join(question).on(question.id.eq(answer.questionId), question.deletedAt.isNull)
        .where(answer.id.eq(answerId), answer.deletedAt.isNull)
        .fetchFirst()
        ?.let { row ->
            SpotQaOriginSource(
                catalogId = row.get(answer.catalogId)!!,
                questionId = row.get(question.id)!!,
                questionBody = row.get(question.body)!!,
                answerId = row.get(answer.id)!!,
                answerBody = row.get(answer.body)!!,
                answerAuthorId = row.get(answer.authorId)!!,
            )
        }

    /** 소유자 종류별 사진 키. 첨부 순서대로. */
    private fun findPhotoKeys(
        ownerType: SpotQaPhotoOwnerType,
        ownerIds: List<Long>,
    ): Map<Long, List<String>> {
        if (ownerIds.isEmpty()) return emptyMap()

        return queryFactory
            .select(photo.ownerId, photo.objectKey)
            .from(photo)
            .where(photo.ownerType.eq(ownerType), photo.ownerId.`in`(ownerIds))
            .orderBy(photo.position.asc())
            .fetch()
            .groupBy({ it.get(photo.ownerId)!! }, { it.get(photo.objectKey)!! })
    }

    /** 삭제되지 않았고, 차단 관계가 아니며, 숨겨졌다면 작성자 본인인 질문(AC-11.6). */
    private fun visibleToViewer(viewer: ViewerContext): BooleanExpression {
        var condition = question.deletedAt.isNull
        blockExclusion(viewer, question.authorId)?.let { condition = condition.and(it) }
        condition = condition.and(hiddenExclusion(viewer, question.hidden, question.authorId))
        return condition
    }

    /** id 목록 필터. null은 "좁히지 않음", 빈 목록은 "결과 없음"이다. */
    private fun idsCondition(ids: List<Long>?): BooleanExpression? = when {
        ids == null -> null
        ids.isEmpty() -> Expressions.FALSE.isTrue
        else -> question.id.`in`(ids)
    }

    /** 활성 언어 필터(SPEC-0029). 장소가 이 언어에서 해소되지 않는 질문을 뺀다(오류 아님). */
    private fun languageCondition(language: ContentLanguage): BooleanExpression = question.catalogId.`in`(
        JPAExpressions.select(catalogLanguage.catalogId).from(catalogLanguage)
            .where(catalogLanguage.language.eq(language)),
    )

    /** 차단 제외. 차단 집합이 비면 null(조건 없음). */
    private fun blockExclusion(
        viewer: ViewerContext,
        authorIdPath: NumberPath<Long>,
    ): BooleanExpression? {
        if (viewer.blockedMemberIds.isEmpty()) return null
        return authorIdPath.notIn(viewer.blockedMemberIds)
    }

    /** 숨겨진 콘텐츠는 작성자 본인에게만 보인다(AC-11.5). */
    private fun hiddenExclusion(
        viewer: ViewerContext,
        hiddenPath: BooleanPath,
        authorIdPath: NumberPath<Long>,
    ): BooleanExpression = hiddenPath.isFalse.or(authorIdPath.eq(viewer.memberId))

    /** `(resolved ASC, createdAt DESC, id DESC)` keyset 조건. 첫 키만 방향이 반대(ASC)다. */
    private fun questionKeysetCondition(cursor: SpotQuestionCursor): BooleanExpression {
        val sameGroup = question.resolved.eq(cursor.resolved)
        val laterGroup = question.resolved.gt(cursor.resolved)
        val withinGroup = question.createdAt.lt(cursor.createdAt)
            .or(question.createdAt.eq(cursor.createdAt).and(question.id.lt(cursor.id)))
        return laterGroup.or(sameGroup.and(withinGroup))
    }

    /** `(createdAt DESC, id DESC)` keyset 조건. */
    private fun keysetCondition(
        createdAtPath: DateTimePath<java.time.Instant>,
        idPath: NumberPath<Long>,
        cursor: CommunityCursor,
    ): BooleanExpression = createdAtPath.lt(cursor.createdAt)
        .or(createdAtPath.eq(cursor.createdAt).and(idPath.lt(cursor.id)))

    /** 엔티티를 read DTO로. 사진·작성자는 지연 필드가 채운다. */
    private fun SpotQuestionEntity.toQuestionView(viewer: ViewerContext) = SpotQuestionView(
        id = requireNotNull(id),
        catalogId = catalogId,
        body = body,
        authorId = authorId,
        resolved = resolved,
        answerCount = answerCount,
        ownedByViewer = authorId == viewer.memberId,
        hidden = hidden,
        createdAt = requireNotNull(createdAt),
    )

    private fun SpotAnswerEntity.toAnswerView(viewer: ViewerContext) = SpotAnswerView(
        id = requireNotNull(id),
        questionId = questionId,
        body = body,
        authorId = authorId,
        ownedByViewer = authorId == viewer.memberId,
        hidden = hidden,
        createdAt = requireNotNull(createdAt),
    )

    private fun SpotAnswerReplyEntity.toReplyView(viewer: ViewerContext) = SpotAnswerReplyView(
        id = requireNotNull(id),
        answerId = answerId,
        body = body,
        authorId = authorId,
        ownedByViewer = authorId == viewer.memberId,
        hidden = hidden,
        createdAt = requireNotNull(createdAt),
    )
}
