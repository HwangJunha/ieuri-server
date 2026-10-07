package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** 글에 연결된 장소 id. 키가 없으면 장소 없는 글이다. */
@DgsDataLoader(name = PostCatalogIdDataLoader.NAME)
@Component
class PostCatalogIdDataLoader(
    private val postQueryUseCase: PostQueryUseCase,
) : MappedBatchLoader<Long, Long> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, Long>> = CompletableFuture.completedFuture(postQueryUseCase.postCatalogIds(postIds.toList()))

    companion object {
        const val NAME = "postCatalogId"
    }
}
