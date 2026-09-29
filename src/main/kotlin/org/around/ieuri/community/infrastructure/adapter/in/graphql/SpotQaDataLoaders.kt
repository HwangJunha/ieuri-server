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

/*
 * 장소 질문/답변의 배치 로더(SPEC-0026). 비로그인(뷰어 0)을 허용한다(AC-1.4).
 */

/**
 * 답변의 답글. 뷰어를 keyContext로 받는다(SPEC-0032 R27).
 * - 배치 디스패치는 리졸버와 다른 스레드에서 돌므로 여기서 Security 컨텍스트를 읽지 않는다.
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
        // 답글이 없는 답변은 빈 목록으로 채운다.
        return CompletableFuture.completedFuture(answerIds.associateWith { replies[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReply"
    }
}

/**
 * 뷰어 신원을 keyContext로 싣기 위한 래퍼.
 * - 맨 `Long`으로 바꾸면 `filterIsInstance`가 키 id와 구별하지 못한다.
 */
@JvmInline
value class ViewerId(val value: Long)

/**
 * 배치에 실려 온 뷰어를 꺼낸다(SPEC-0032 R27). 한 배치의 뷰어는 하나다.
 * - 없으면 비로그인으로 폴백하지 않고 실패한다.
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

/** 비로그인 호출자. 어떤 회원 id와도 겹치지 않는다. */
internal const val ANONYMOUS = 0L
