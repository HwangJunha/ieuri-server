package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.loader
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.adapter.graphql.contentLanguage
import org.around.ieuri.community.adapter.graphql.mapper.toGql
import org.around.ieuri.community.adapter.graphql.mapper.toGqlOrPlaceholder
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerView
import org.around.ieuri.community.application.question.provided.dto.SpotAnswersQuery
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionView
import org.around.ieuri.community.application.question.provided.dto.SpotQuestionsQuery
import org.around.ieuri.community.application.shared.dto.PostAuthorView
import org.around.ieuri.community.application.shared.dto.PostSpotView
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostSpot as GqlPostSpot
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * 장소 질문/답변 조회 리졸버(SPEC-0026). read DTO를 노드로 반환하고 사진·작성자·장소·답글은 요청 시에만 배치로 읽는다.
 * - 인증 없이도 읽힌다(AC-1.4). 비로그인은 뷰어 0 — 차단·소유 관계가 없는 호출자다.
 */
@DgsComponent
@Component
class SpotQaQueryResolver(
    private val spotQuestionQueryUseCase: SpotQuestionQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /**
     * 질문 목록. 장소·작성자·id 세 필터는 독립적이며 함께 걸린다.
     * - `mine = true`를 비로그인이 부르면 빈 목록이 아니라 UNAUTHENTICATED 오류다.
     * - `catalogId`의 존재 검사는 활성 언어 기준이다(SPEC-0029 R-16).
     */
    @DgsQuery
    fun spotQuestions(
        @InputArgument catalogId: Long?,
        @InputArgument mine: Boolean?,
        @InputArgument ids: List<Long>?,
        @InputArgument first: Int?,
        @InputArgument after: String?,
        dfe: DgsDataFetchingEnvironment,
    ): SpotQuestionConnection {
        val onlyMine = mine ?: false
        val page = spotQuestionQueryUseCase.spotQuestions(
            SpotQuestionsQuery(
                catalogId = catalogId,
                mine = onlyMine,
                ids = ids,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            memberId = if (onlyMine) currentMemberProvider.requireMemberId() else viewerId(dfe),
            language = dfe.contentLanguage(),
        )
        return SpotQuestionConnection(
            edges = page.edges.map { SpotQuestionEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    @DgsQuery
    fun spotAnswers(
        @InputArgument questionId: Long,
        @InputArgument first: Int?,
        @InputArgument after: String?,
        dfe: DgsDataFetchingEnvironment,
    ): SpotAnswerConnection {
        val page = spotQuestionQueryUseCase.spotAnswers(
            SpotAnswersQuery(
                questionId = questionId,
                first = first ?: DEFAULT_PAGE_SIZE,
                after = after,
            ),
            memberId = viewerId(dfe),
        )
        return SpotAnswerConnection(
            edges = page.edges.map { SpotAnswerEdge(cursor = it.cursor, node = it.node) },
            pageInfo = page.pageInfo.toGql(),
        )
    }

    // ── SpotQuestion 지연 필드 ────────────────────────────────────────

    /**
     * 질문이 달린 장소. non-null이다.
     * - 조회가 비면 데이터 손상으로 보고 실패한다(활성 언어 미해소 질문은 목록이 이미 거른다, SPEC-0029 R-23).
     */
    @DgsData(parentType = "SpotQuestion", field = "spot")
    fun questionSpot(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostSpot> {
        val catalogId = dfe.questionSource().catalogId
        return dfe.loader<PostSpotView>(PostSpotDataLoader.NAME)
            .load(catalogId, dfe.contentLanguage())
            .thenApply { summary ->
                requireNotNull(summary) { "질문이 가리키는 장소($catalogId)를 찾을 수 없다." }.toGql()
            }
    }

    @DgsData(parentType = "SpotQuestion", field = "author")
    fun questionAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadAuthor(dfe.questionSource().authorId)

    @DgsData(parentType = "SpotQuestion", field = "photoKeys")
    fun questionPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> = dfe.loader<List<String>>(SpotQuestionPhotoDataLoader.NAME).load(dfe.questionSource().id)

    // ── SpotAnswer 지연 필드 ─────────────────────────────────────────

    @DgsData(parentType = "SpotAnswer", field = "author")
    fun answerAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadAuthor(dfe.answerSource().authorId)

    @DgsData(parentType = "SpotAnswer", field = "photoKeys")
    fun answerPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> = dfe.loader<List<String>>(SpotAnswerPhotoDataLoader.NAME).load(dfe.answerSource().id)

    /**
     * 답글. 요청된 경우에만 배치로 읽는다.
     * - 뷰어는 여기서 확정해 로더에 넘긴다 — 로더 스레드에서 읽으면 비로그인으로 강등된다(SPEC-0032 R27).
     */
    @DgsData(parentType = "SpotAnswer", field = "replies")
    fun answerReplies(
        dfe: DgsDataFetchingEnvironment,
    ): CompletableFuture<List<SpotAnswerReplyView>> = dfe.loader<List<SpotAnswerReplyView>>(SpotAnswerReplyDataLoader.NAME)
        .load(dfe.answerSource().id, ViewerId(viewerId(dfe)))

    // ── SpotAnswerReply 지연 필드 ────────────────────────────────────

    @DgsData(parentType = "SpotAnswerReply", field = "author")
    fun replyAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> = dfe.loadAuthor(dfe.replySource().authorId)

    @DgsData(parentType = "SpotAnswerReply", field = "photoKeys")
    fun replyPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> = dfe.loader<List<String>>(SpotAnswerReplyPhotoDataLoader.NAME).load(dfe.replySource().id)

    // ── 공통 ─────────────────────────────────────────────────────────

    /**
     * 뷰어 신원. 비로그인은 0이다.
     * - 거절된(만료 등) 토큰은 비로그인(0)으로 흘리지 않는다(SPEC-0032 R1).
     */
    private fun viewerId(dfe: DgsDataFetchingEnvironment): Long = currentMemberProvider.optionalMemberId(dfe.tokenPresentation()) ?: ANONYMOUS

    /** 작성자를 배치로 읽는다. 회원 정보가 없으면(탈퇴) 대체 표기로 채운다. */
    private fun DgsDataFetchingEnvironment.loadAuthor(
        memberId: Long,
    ): CompletableFuture<GqlPostAuthor> = loader<PostAuthorView>(PostAuthorDataLoader.NAME)
        .load(memberId)
        .thenApply { it.toGqlOrPlaceholder(memberId) }

    /** 부모 리졸버가 돌려준 read DTO를 꺼낸다. 없으면 명시적으로 실패한다. */
    private fun DgsDataFetchingEnvironment.questionSource(): SpotQuestionView = getSource<SpotQuestionView>()
        ?: error("SpotQuestion 필드 리졸버는 부모가 반환한 SpotQuestionView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.answerSource(): SpotAnswerView = getSource<SpotAnswerView>()
        ?: error("SpotAnswer 필드 리졸버는 부모가 반환한 SpotAnswerView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.replySource(): SpotAnswerReplyView = getSource<SpotAnswerReplyView>()
        ?: error("SpotAnswerReply 필드 리졸버는 부모가 반환한 SpotAnswerReplyView를 소스로 받아야 한다.")

    private fun PostSpotView.toGql(): GqlPostSpot = GqlPostSpot(
        id = id,
        title = title,
        thumbnailImageUrl = thumbnailImageUrl,
        category = category?.let { GqlTargetCategory.valueOf(it.name) },
    )

    private companion object {
        /** 커넥션 기본 페이지 크기. 상한(50) 절삭은 응용 계층이 한다. */
        const val DEFAULT_PAGE_SIZE = 20
    }
}
