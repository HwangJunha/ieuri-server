package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = SpotAnswerPhotoDataLoader.NAME)
@Component
class SpotAnswerPhotoDataLoader(
    private val spotQuestionQueryUseCase: SpotQuestionQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(answerIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQuestionQueryUseCase.answerPhotoKeys(answerIds.toList())
        return CompletableFuture.completedFuture(answerIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerPhoto"
    }
}
