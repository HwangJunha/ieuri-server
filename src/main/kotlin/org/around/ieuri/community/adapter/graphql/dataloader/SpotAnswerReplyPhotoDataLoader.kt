package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = SpotAnswerReplyPhotoDataLoader.NAME)
@Component
class SpotAnswerReplyPhotoDataLoader(
    private val spotQuestionQueryUseCase: SpotQuestionQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(replyIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQuestionQueryUseCase.replyPhotoKeys(replyIds.toList())
        return CompletableFuture.completedFuture(replyIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReplyPhoto"
    }
}
