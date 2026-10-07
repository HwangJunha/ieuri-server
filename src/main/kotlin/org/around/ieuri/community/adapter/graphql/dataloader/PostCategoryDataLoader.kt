package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.around.ieuri.community.domain.post.TargetCategory
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** 글 카테고리. 연결이 없으면 빈 값으로 덮지 않고 키를 비워 리졸버가 실패하게 둔다. */
@DgsDataLoader(name = PostCategoryDataLoader.NAME)
@Component
class PostCategoryDataLoader(
    private val postQueryUseCase: PostQueryUseCase,
) : MappedBatchLoader<Long, TargetCategory> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, TargetCategory>> = CompletableFuture.completedFuture(postQueryUseCase.postCategories(postIds.toList()))

    companion object {
        const val NAME = "postCategory"
    }
}
