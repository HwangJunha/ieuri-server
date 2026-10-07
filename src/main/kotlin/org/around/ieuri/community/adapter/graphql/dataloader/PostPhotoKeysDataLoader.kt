package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

@DgsDataLoader(name = PostPhotoKeysDataLoader.NAME)
@Component
class PostPhotoKeysDataLoader(
    private val postQueryUseCase: PostQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = postQueryUseCase.postPhotoKeys(postIds.toList())
        return CompletableFuture.completedFuture(postIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "postPhotoKeys"
    }
}
