package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.around.ieuri.community.application.question.provided.dto.SpotAnswerReplyView
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 답변의 답글. 뷰어를 keyContext로 받는다(SPEC-0032 R27).
 * - 배치 디스패치는 리졸버와 다른 스레드에서 돌므로 여기서 Security 컨텍스트를 읽지 않는다.
 */
@DgsDataLoader(name = SpotAnswerReplyDataLoader.NAME)
@Component
class SpotAnswerReplyDataLoader(
    private val spotQuestionQueryUseCase: SpotQuestionQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<SpotAnswerReplyView>> {

    override fun load(
        answerIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<SpotAnswerReplyView>>> {
        val viewer = environment.requireViewer("SpotAnswerReplyDataLoader")
        val replies = spotQuestionQueryUseCase.answerReplies(answerIds.toList(), viewer)
        // 답글이 없는 답변은 빈 목록으로 채운다.
        return CompletableFuture.completedFuture(answerIds.associateWith { replies[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReply"
    }
}
