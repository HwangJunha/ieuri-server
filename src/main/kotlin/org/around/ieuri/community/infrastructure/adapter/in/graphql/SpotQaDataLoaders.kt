package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.port.`in`.query.SpotQaQueryUseCase
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 장소 질문/답변의 배치 로더(SPEC-0026).
 *
 * 지연 필드는 요청당 과다 조회를 막고 DataLoader는 요청 **안**의 N+1을 막는 것이라,
 * 둘은 다른 문제이며 둘 다 필요하다.
 *
 * **비로그인을 허용한다** — 질문 목록은 인증 없이 읽히므로(AC-1.4) 0이 "차단 관계도 소유
 * 관계도 없는 호출자"를 뜻한다.
 */

/**
 * 답변의 답글. **뷰어를 keyContext로 받는다**(SPEC-0032 R27).
 *
 * 이전에는 `load()` 안에서 `CurrentMemberProvider`(ThreadLocal)를 읽었다. 요청 간 오염은
 * 없었지만 — DGS가 요청마다 레지스트리를 새로 만든다 — **가상 스레드가 켜져 있어 배치
 * 디스패치가 리졸버와 다른 스레드에서 돈다.** Security 컨텍스트가 그 스레드로 전파되지
 * 않으면 뷰어가 조용히 `ANONYMOUS`로 강등되어, 차단 관계와 소유 관계가 사라진 **그럴듯한
 * 오답**이 나갔다. 오류보다 나쁘다.
 *
 * 뷰어도 언어와 같은 요청 범위 값이므로 `PostSpotDataLoader`와 같은 방식으로 넘긴다 —
 * 리졸버가 자기 스레드에서 신원을 확정해 `load(key, viewer)`로 싣는다.
 * **폴백하지 않고 실패한다**: 요청이 언제나 갖는 값이라 부재는 리졸버의 결함이다.
 */
@DgsDataLoader(name = SpotAnswerReplyDataLoader.NAME)
@Component
class SpotAnswerReplyDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<SpotAnswerReplyView>> {

    override fun load(
        answerIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<SpotAnswerReplyView>>> {
        val viewer = environment.requireViewer("SpotAnswerReplyDataLoader")
        val replies = spotQaQueryUseCase.answerReplies(answerIds.toList(), viewer)
        // 답글이 없는 답변은 결과에 키가 없다 — 빈 목록으로 채워야 리졸버가 null을 만나지 않는다.
        return CompletableFuture.completedFuture(answerIds.associateWith { replies[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReply"
    }
}

/**
 * 뷰어 신원을 keyContext로 싣기 위한 래퍼.
 *
 * `Long`을 그대로 넘기지 않는 이유는 `filterIsInstance`가 **키(답변 id)와 구별하지 못하기**
 * 때문이다 — 둘 다 `Long`이면 엉뚱한 값이 뷰어로 뽑힌다.
 */
@JvmInline
value class ViewerId(val value: Long)

/**
 * 배치에 실려 온 뷰어를 꺼낸다(SPEC-0032 R27).
 *
 * 한 배치의 키는 전부 같은 요청에서 오므로 뷰어도 하나다 — 아무 keyContext나 집어 쓴다.
 *
 * **폴백하지 않는다.** 요청이 언제나 갖는 값이라 부재는 리졸버가 `load(key, viewer)`를
 * 빠뜨렸다는 뜻이고, 비로그인으로 흘리면 차단·소유 관계가 사라진 그럴듯한 오답이 나간다.
 */
internal fun BatchLoaderEnvironment.requireViewer(loaderName: String): Long = keyContexts.values
    .filterIsInstance<ViewerId>()
    .firstOrNull()
    ?.value
    ?: error("${loaderName}은 keyContext로 뷰어를 받아야 한다 — 리졸버가 load(key, ViewerId(...))로 부른다.")

@DgsDataLoader(name = SpotQuestionPhotoDataLoader.NAME)
@Component
class SpotQuestionPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(questionIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.questionPhotoKeys(questionIds.toList())
        return CompletableFuture.completedFuture(questionIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotQuestionPhoto"
    }
}

@DgsDataLoader(name = SpotAnswerPhotoDataLoader.NAME)
@Component
class SpotAnswerPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(answerIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.answerPhotoKeys(answerIds.toList())
        return CompletableFuture.completedFuture(answerIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerPhoto"
    }
}

@DgsDataLoader(name = SpotAnswerReplyPhotoDataLoader.NAME)
@Component
class SpotAnswerReplyPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(replyIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.replyPhotoKeys(replyIds.toList())
        return CompletableFuture.completedFuture(replyIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReplyPhoto"
    }
}

/** 비로그인 호출자. 차단 집합도 소유 관계도 없어 어떤 회원 id와도 겹치지 않는 값이다. */
internal const val ANONYMOUS = 0L
