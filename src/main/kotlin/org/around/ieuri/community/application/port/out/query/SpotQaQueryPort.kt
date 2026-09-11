package org.around.ieuri.community.application.port.out.query

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.around.ieuri.community.application.query.SpotAnswerView
import org.around.ieuri.community.application.query.SpotQaCounts
import org.around.ieuri.community.application.query.SpotQuestionView
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 장소 질문/답변 읽기 아웃바운드 포트(SPEC-0026). 애그리게이트를 우회해 필요한 값만 프로젝션한다.
 */
interface SpotQaQueryPort {
    /**
     * 질문 목록. 정렬은 `resolved ASC, createdAt DESC, id DESC`이며 인자로 바꿀 수 없다.
     *
     * 커서는 **정렬 키 전체**를 담는다 — createdAt만 담으면 resolved가 바뀐 질문이 페이지
     * 경계에서 중복되거나 누락된다.
     */
    fun findQuestions(search: SpotQuestionsSearch): CursorPage<SpotQuestionView>

    /**
     * 질문 단건. 차단 필터는 항상 걸리며, 걸러지면 null이다.
     *
     * [includeHidden]이 true면 **숨김 필터만 우회한다** — 숨겨진 질문도 돌려주므로 호출자가
     * `hidden`을 보고 판단할 수 있다. 답변 목록이 R11의 종속(질문이 숨겨지면 답변도 가려짐)을
     * 적용하려면 "숨겨진 질문이 존재한다"와 "질문이 없다"를 구분해야 하는데, 숨김 필터를
     * 거치면 둘 다 null이 되어 구분이 사라진다.
     *
     * **차단은 우회하지 않는다.** 차단 상대의 질문은 이 플래그와 무관하게 null이며,
     * 그래야 차단이 답변 목록으로 우회되지 않는다.
     */
    fun findQuestion(
        questionId: Long,
        viewer: ViewerContext,
        includeHidden: Boolean = false,
    ): SpotQuestionView?

    /** 답변 목록. 최신순. 질문의 숨김 종속(R11)은 호출자가 이미 판정한 뒤 부른다. */
    fun findAnswers(search: SpotAnswersSearch): CursorPage<SpotAnswerView>

    /**
     * 여러 답변의 답글을 **한 번에**. 오래된 순이며 페이지네이션이 없다.
     * 답글이 없는 답변은 결과에 키가 없다.
     */
    fun findRepliesOf(
        answerIds: List<Long>,
        viewer: ViewerContext,
    ): Map<Long, List<SpotAnswerReplyView>>

    /**
     * 아래 셋은 전부 여러 건을 한 번에 읽는다 — DataLoader가 하나씩 물고 있어, 클라이언트가
     * 그 필드를 고른 경우에만 한 번의 쿼리로 가져온다. 사진이 없으면 결과에 키가 없다.
     */
    fun findQuestionPhotoKeys(questionIds: List<Long>): Map<Long, List<String>>

    fun findAnswerPhotoKeys(answerIds: List<Long>): Map<Long, List<String>>

    fun findReplyPhotoKeys(replyIds: List<Long>): Map<Long, List<String>>

    /**
     * 장소별 질문·답변 수(SPEC-0035 R9). 삭제·숨김은 빼며, **요청한 id 전부에 값이 있다** —
     * 질문이 없는 장소는 0이다. 답글은 세지 않는다.
     */
    fun countByCatalogIds(catalogIds: Collection<Long>): Map<Long, SpotQaCounts>

    /**
     * DM 출처 스냅샷의 재료(SPEC-0026 R14). 답변 하나에서 질문·장소까지 **한 번에** 읽는다.
     *
     * 삭제된 답변·질문이면 null이며 그것이 "출처를 만들 수 없다"는 판정이다 — 호출자는
     * 거절하지 않고 출처 없이 대화를 연다(AC-13.4).
     *
     * 장소명은 여기 담기지 않는다 — tourism이 소유한 값이라 호출자가 자기 포트로 읽는다.
     */
    fun findAnswerOriginSource(answerId: Long): SpotQaOriginSource?
}

/**
 * 출처 스냅샷을 만들 재료. **본문 전체를 담는다** — 100자 절삭은 값 객체가 하며, 조회가
 * 미리 자르면 그 규칙이 두 곳에 생긴다.
 */
data class SpotQaOriginSource(
    val catalogId: Long,
    val questionId: Long,
    val questionBody: String,
    val answerId: Long,
    val answerBody: String,
    /** 답변 작성자. 대화 상대가 이 사람인지 호출자가 판정한다. */
    val answerAuthorId: Long,
)

/**
 * 질문 목록 조회 조건.
 *
 * [ViewerContext]를 그대로 재사용한다 — 차단 필터가 글·댓글과 완전히 같기 때문이다.
 * `hiddenPostIds`는 글 단위 개인 숨김이라 여기서는 비어 있는 채로 온다(질문에는 개인 숨김이 없다).
 */
data class SpotQuestionsSearch(
    /** 이 장소의 질문만. null이면 좁히지 않는다. */
    val catalogId: Long?,
    /** 이 작성자의 질문만. null이면 좁히지 않는다 — 호출자 신원은 응용 계층이 채운다. */
    val authorId: Long? = null,
    /** 이 id들만. null이면 좁히지 않고, **빈 목록이면 결과가 없다.** */
    val ids: List<Long>? = null,
    /**
     * 요청의 활성 언어. **무조건 걸린다**(SPEC-0029 R-23) — 질문은 장소가 필수라(catalog_id
     * NOT NULL) 글과 달리 "장소 없는 항목"이라는 예외가 없다. 이 언어에서 해소되지 않는
     * 장소의 질문은 오류가 아니라 목록에서 빠진다.
     */
    val language: ContentLanguage,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)

data class SpotAnswersSearch(
    val questionId: Long,
    val first: Int,
    val after: String?,
    val viewer: ViewerContext,
)
