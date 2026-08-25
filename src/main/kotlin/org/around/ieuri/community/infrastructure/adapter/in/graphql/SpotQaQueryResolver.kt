package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.common.graphql.tokenPresentation
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.SpotQaQueryUseCase
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotAnswersQuery
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.application.query.SpotQuestionsQuery
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.community.infrastructure.adapter.`in`.graphql.mapper.toGqlOrPlaceholder
import org.around.ieuri.community.infrastructure.locale.contentLanguage
import org.around.ieuri.common.graphql.loader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.PostAuthor as GqlPostAuthor
import org.around.ieuri.common.graphql.generated.types.PostSpot as GqlPostSpot
import org.around.ieuri.common.graphql.generated.types.TargetCategory as GqlTargetCategory

/**
 * 장소 질문/답변 조회 리졸버(SPEC-0026).
 *
 * **인증 없이도 읽힌다**(AC-1.4) — `requireMemberId`가 아니라 `currentMemberId`를 쓰고,
 * 비로그인은 0으로 흘러 차단 집합도 소유 관계도 없는 호출자가 된다.
 *
 * 루트는 **read DTO를 노드로 실어 돌려준다** — 생성 타입은 non-null 필드를 생성자에서
 * 요구해 지연 필드를 비워 둘 수 없기 때문이다. 사진·작성자·장소·답글은 요청된 경우에만
 * @DgsData가 배치로 읽는다.
 */
@DgsComponent
@Component
class SpotQaQueryResolver(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {

    /**
     * 질문 목록. 장소·작성자·id 세 필터가 독립적이며 함께 걸린다.
     *
     * **`mine = true`는 인증을 요구한다** — 비로그인은 좁힐 대상이 없어 빈 목록을 주는 대신
     * `requireMemberId`가 최상위 errors[]로 UNAUTHENTICATED를 낸다. 조용히 빈 목록을 주면
     * 클라이언트가 "질문을 쓴 적 없음"으로 오해한다.
     *
     * `catalogId` 필터의 존재 검사는 **활성 언어 기준이다**(SPEC-0029 R-16, AC-15.3).
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
        val page = spotQaQueryUseCase.spotQuestions(
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
        val page = spotQaQueryUseCase.spotAnswers(
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
     * 질문이 달린 장소. **질문은 언제나 장소에 속하므로 non-null이다** — 조회가 비면
     * 카탈로그가 사라진 것이고, 그것은 데이터 손상이라 조용히 넘기지 않는다.
     *
     * **활성 언어에서 해소되지 않는 질문은 여기 오지 않는다**(SPEC-0029 R-23·AC-23.3) —
     * 목록 조회가 이미 걸러낸다. 그러므로 여기서의 부재는 여전히 데이터 손상이다.
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
    fun questionAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.questionSource().authorId)

    @DgsData(parentType = "SpotQuestion", field = "photoKeys")
    fun questionPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(SpotQuestionPhotoDataLoader.NAME).load(dfe.questionSource().id)

    // ── SpotAnswer 지연 필드 ─────────────────────────────────────────

    @DgsData(parentType = "SpotAnswer", field = "author")
    fun answerAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.answerSource().authorId)

    @DgsData(parentType = "SpotAnswer", field = "photoKeys")
    fun answerPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(SpotAnswerPhotoDataLoader.NAME).load(dfe.answerSource().id)

    /**
     * 답글. **요청된 경우에만** 배치로 읽는다 — 목록만 그리는 화면은 조회가 붙지 않는다.
     *
     * **뷰어를 여기서 확정해 실어 보낸다**(SPEC-0032 R27) — 로더가 자기 스레드에서 읽으면
     * 가상 스레드 전환에 신원을 잃고 조용히 비로그인으로 강등된다.
     */
    @DgsData(parentType = "SpotAnswer", field = "replies")
    fun answerReplies(
        dfe: DgsDataFetchingEnvironment,
    ): CompletableFuture<List<SpotAnswerReplyView>> =
        dfe.loader<List<SpotAnswerReplyView>>(SpotAnswerReplyDataLoader.NAME)
            .load(dfe.answerSource().id, ViewerId(viewerId(dfe)))

    // ── SpotAnswerReply 지연 필드 ────────────────────────────────────

    @DgsData(parentType = "SpotAnswerReply", field = "author")
    fun replyAuthor(dfe: DgsDataFetchingEnvironment): CompletableFuture<GqlPostAuthor> =
        dfe.loadAuthor(dfe.replySource().authorId)

    @DgsData(parentType = "SpotAnswerReply", field = "photoKeys")
    fun replyPhotoKeys(dfe: DgsDataFetchingEnvironment): CompletableFuture<List<String>> =
        dfe.loader<List<String>>(SpotAnswerReplyPhotoDataLoader.NAME).load(dfe.replySource().id)

    // ── 공통 ─────────────────────────────────────────────────────────

    /**
     * 뷰어 신원. 비로그인은 0이다 — 차단 집합도 소유 관계도 없는 호출자를 뜻한다.
     *
     * **거절된 토큰은 0으로 흘리지 않는다**(SPEC-0032 R1). 만료를 비로그인으로 다루면 차단
     * 관계와 소유 관계가 조용히 사라져 **그럴듯한 오답**이 나간다 — 오류보다 나쁘다.
     */
    private fun viewerId(dfe: DgsDataFetchingEnvironment): Long =
        currentMemberProvider.optionalMemberId(dfe.tokenPresentation()) ?: ANONYMOUS

    /**
     * 작성자를 배치로 읽는다. 회원 정보가 없어도 질문·답변은 남으므로(탈퇴해도 보존한다)
     * 대체 표기로 채운다.
     */
    private fun DgsDataFetchingEnvironment.loadAuthor(
        memberId: Long,
    ): CompletableFuture<GqlPostAuthor> =
        loader<PostAuthorView>(PostAuthorDataLoader.NAME)
            .load(memberId)
            .thenApply { it.toGqlOrPlaceholder(memberId) }

    /**
     * 부모 리졸버가 돌려준 read DTO를 꺼낸다.
     *
     * `!!` 대신 명시적으로 실패시킨다 — 부모가 반환한 값이라 실제로 null이 될 수 없지만,
     * 그 보장은 DGS의 실행 순서에 달려 있어 타입이 증명하지 못한다.
     */
    private fun DgsDataFetchingEnvironment.questionSource(): SpotQuestionView =
        getSource<SpotQuestionView>()
            ?: error("SpotQuestion 필드 리졸버는 부모가 반환한 SpotQuestionView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.answerSource(): SpotAnswerView =
        getSource<SpotAnswerView>()
            ?: error("SpotAnswer 필드 리졸버는 부모가 반환한 SpotAnswerView를 소스로 받아야 한다.")

    private fun DgsDataFetchingEnvironment.replySource(): SpotAnswerReplyView =
        getSource<SpotAnswerReplyView>()
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
