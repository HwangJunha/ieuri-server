package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.PostQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** 필드가 선택됐을 때만 페이지 단위로 읽는다. 값이 없는 글은 빈 목록으로 채운다 — 키를 빠뜨리면 리졸버가 null을 받는다. */
@DgsDataLoader(name = PostTagsDataLoader.NAME)
@Component
class PostTagsDataLoader(
    private val postQueryUseCase: PostQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val tags = postQueryUseCase.postTags(postIds.toList())
        return CompletableFuture.completedFuture(postIds.associateWith { tags[it].orEmpty() })
    }

    companion object {
        const val NAME = "postTags"
    }
}
