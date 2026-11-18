package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** 뷰어가 좋아요한 글인지. 뷰어는 keyContext로 받는다(SPEC-0032 R27). */
@DgsDataLoader(name = PostLikedDataLoader.NAME)
@Component
class PostLikedDataLoader(
    private val postQueryUseCase: PostQueryUseCase,
) : MappedBatchLoaderWithContext<Long, Boolean> {

    override fun load(
        postIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, Boolean>> {
        val memberId = environment.requireViewer("PostLikedDataLoader")
        val liked = postQueryUseCase.likedPostIds(postIds.toList(), memberId)
        return CompletableFuture.completedFuture(postIds.associateWith { it in liked })
    }

    companion object {
        const val NAME = "postLiked"
    }
}
