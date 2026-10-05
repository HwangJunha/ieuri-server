package org.around.ieuri.community.adapter.persistence.query

import org.around.ieuri.common.pagination.CursorEdge
import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.adapter.persistence.SpotQaPhotoOwner
import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswerReplyView
import org.around.ieuri.community.adapter.persistence.mapper.toSpotAnswerView
import org.around.ieuri.community.adapter.persistence.mapper.toSpotQuestionView
import org.around.ieuri.community.adapter.persistence.mapper.toTimestamptz
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerReplyTable
import org.around.ieuri.community.adapter.persistence.table.SpotAnswerTable
import org.around.ieuri.community.adapter.persistence.table.SpotCatalogLanguageTable
import org.around.ieuri.community.adapter.persistence.table.SpotQaPhotoTable
import org.around.ieuri.community.adapter.persistence.table.SpotQuestionTable
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.question.required.SpotAnswersSearch
import org.around.ieuri.community.application.question.required.SpotQaQueryPort
import org.around.ieuri.community.application.question.required.SpotQuestionsSearch
import org.around.ieuri.community.application.shared.required.ViewerContext
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.springframework.stereotype.Component

/** 장소 질문/답변 읽기. 차단·숨김 필터는 커뮤니티 글과 같은 모양이다. 트랜잭션은 조회 서비스가 연다. */
@Component
class SpotQaQueryAdapter : SpotQaQueryPort {

    /** 정렬은 미해결 우선, 그 안에서 최신순으로 고정이다(`resolved ASC` = false 먼저). */
    override fun findQuestions(search: SpotQuestionsSearch): CursorPage<SpotQuestionView> {
        val cursor = search.after?.let(::decodeSpotQuestionCursor)
        val rows = SpotQuestionTable.selectAll()
            .where {
                allOf(
                    search.catalogId?.let { SpotQuestionTable.catalogId eq it },
                    search.authorId?.let { SpotQuestionTable.authorId eq it },
                    // null은 "좁히지 않음", 빈 목록은 "결과 없음"이다.
                    search.ids?.let { if (it.isEmpty()) Op.FALSE else SpotQuestionTable.id inList it },
                    SpotQuestionTable.catalogId inSubQuery SpotCatalogLanguageTable.select(SpotCatalogLanguageTable.catalogId)
                        .where { SpotCatalogLanguageTable.language eq search.language.name },
                    visibleToViewer(search.viewer),
                    cursor?.let(::questionKeysetAfter),
                )
            }
            .orderBy(SpotQuestionTable.resolved to SortOrder.ASC, SpotQuestionTable.createdAt to SortOrder.DESC, SpotQuestionTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            CursorEdge(
                encodeSpotQuestionCursor(row[SpotQuestionTable.resolved], row[SpotQuestionTable.createdAt].toInstant(), row[SpotQuestionTable.id]),
                row.toSpotQuestionView(search.viewer.memberId),
            )
        }
    }

    /** 차단은 언제나 건다. 숨김만 [includeHidden]으로 우회한다(종속 판정용). */
    override fun findQuestion(questionId: Long, viewer: ViewerContext, includeHidden: Boolean): SpotQuestionView? = SpotQuestionTable.selectAll()
        .where {
            allOf(
                SpotQuestionTable.id eq questionId,
                SpotQuestionTable.deletedAt.isNull(),
                blockExclusion(viewer, SpotQuestionTable.authorId),
                if (includeHidden) null else hiddenExclusion(viewer, SpotQuestionTable.hidden, SpotQuestionTable.authorId),
            )
        }
        .singleOrNull()?.toSpotQuestionView(viewer.memberId)

    /** 최신순. 질문의 숨김 종속은 여기서 보지 않는다 — 호출자가 질문을 먼저 판정한 뒤 부른다. */
    override fun findAnswers(search: SpotAnswersSearch): CursorPage<SpotAnswerView> {
        val cursor = search.after?.let(::decodeCursor)
        val rows = SpotAnswerTable.selectAll()
            .where {
                allOf(
                    SpotAnswerTable.questionId eq search.questionId,
                    SpotAnswerTable.deletedAt.isNull(),
                    blockExclusion(search.viewer, SpotAnswerTable.authorId),
                    hiddenExclusion(search.viewer, SpotAnswerTable.hidden, SpotAnswerTable.authorId),
                    cursor?.let { keysetBefore(SpotAnswerTable.createdAt, SpotAnswerTable.id, it) },
                )
            }
            .orderBy(SpotAnswerTable.createdAt to SortOrder.DESC, SpotAnswerTable.id to SortOrder.DESC)
            .limit(search.first + 1)
            .toList()
        return page(rows, search.first, cursor != null) { row ->
            CursorEdge(encodeCursor(row[SpotAnswerTable.createdAt].toInstant(), row[SpotAnswerTable.id]), row.toSpotAnswerView(search.viewer.memberId))
        }
    }

    /** 답글은 오래된 순이며 페이지네이션이 없다. */
    override fun findRepliesOf(answerIds: List<Long>, viewer: ViewerContext): Map<Long, List<SpotAnswerReplyView>> {
        if (answerIds.isEmpty()) return emptyMap()
        return SpotAnswerReplyTable.selectAll()
            .where {
                allOf(
                    SpotAnswerReplyTable.answerId inList answerIds,
                    SpotAnswerReplyTable.deletedAt.isNull(),
                    blockExclusion(viewer, SpotAnswerReplyTable.authorId),
                    hiddenExclusion(viewer, SpotAnswerReplyTable.hidden, SpotAnswerReplyTable.authorId),
                )
            }
            .orderBy(SpotAnswerReplyTable.createdAt to SortOrder.ASC, SpotAnswerReplyTable.id to SortOrder.ASC)
            .groupBy({ it[SpotAnswerReplyTable.answerId] }, { it.toSpotAnswerReplyView(viewer.memberId) })
    }

    override fun findQuestionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwner.QUESTION, questionIds)

    override fun findAnswerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwner.ANSWER, answerIds)

    override fun findReplyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>> = findPhotoKeys(SpotQaPhotoOwner.REPLY, replyIds)

    /** 첨부 순서대로. */
    private fun findPhotoKeys(owner: SpotQaPhotoOwner, ownerIds: List<Long>): Map<Long, List<String>> {
        if (ownerIds.isEmpty()) return emptyMap()
        return SpotQaPhotoTable.select(SpotQaPhotoTable.ownerId, SpotQaPhotoTable.objectKey)
            .where { (SpotQaPhotoTable.ownerType eq owner.name) and (SpotQaPhotoTable.ownerId inList ownerIds) }
            .orderBy(SpotQaPhotoTable.position to SortOrder.ASC)
            .groupBy({ it[SpotQaPhotoTable.ownerId] }, { it[SpotQaPhotoTable.objectKey] })
    }

    /** 삭제되지 않았고, 차단 관계가 아니며, 숨겨졌다면 작성자 본인인 질문. */
    private fun visibleToViewer(viewer: ViewerContext): Op<Boolean> = allOf(
        SpotQuestionTable.deletedAt.isNull(),
        blockExclusion(viewer, SpotQuestionTable.authorId),
        hiddenExclusion(viewer, SpotQuestionTable.hidden, SpotQuestionTable.authorId),
    )

    /** `(resolved ASC, createdAt DESC, id DESC)` keyset. 첫 키만 방향이 반대다 — 미해결 커서 뒤에는 해결된 질문 전체가 온다. */
    private fun questionKeysetAfter(cursor: SpotQuestionCursor): Op<Boolean> {
        val at = cursor.createdAt.toTimestamptz()
        val laterGroup = if (cursor.resolved) Op.FALSE else SpotQuestionTable.resolved eq true
        val withinGroup = (SpotQuestionTable.createdAt less at) or ((SpotQuestionTable.createdAt eq at) and (SpotQuestionTable.id less cursor.id))
        return laterGroup or ((SpotQuestionTable.resolved eq cursor.resolved) and withinGroup)
    }
}
