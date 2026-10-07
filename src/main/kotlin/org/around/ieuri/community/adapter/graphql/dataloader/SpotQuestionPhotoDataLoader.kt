package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.question.provided.SpotQuestionQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = SpotQuestionPhotoDataLoader.NAME)
@Component
class SpotQuestionPhotoDataLoader(
    private val spotQuestionQueryUseCase: SpotQuestionQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(questionIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQuestionQueryUseCase.questionPhotoKeys(questionIds.toList())
        return CompletableFuture.completedFuture(questionIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotQuestionPhoto"
    }
}
