package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.post.provided.CommunityQueryUseCase
import org.around.ieuri.community.domain.shared.TargetCategory
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 글의 지연 필드(태그·사진·카테고리·장소·좋아요) 배치 로더들. 필드가 선택됐을 때만 페이지 단위로 읽는다.
 * - 목록형 값이 없는 글은 빈 값으로 채운다. 키를 빠뜨리면 리졸버가 null을 받는다.
 */

@DgsDataLoader(name = PostTagsDataLoader.NAME)
@Component
class PostTagsDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val tags = communityQueryUseCase.postTags(postIds.toList())
        return CompletableFuture.completedFuture(postIds.associateWith { tags[it].orEmpty() })
    }

    companion object {
        const val NAME = "postTags"
    }
}

@DgsDataLoader(name = PostPhotoKeysDataLoader.NAME)
@Component
class PostPhotoKeysDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = communityQueryUseCase.postPhotoKeys(postIds.toList())
        return CompletableFuture.completedFuture(postIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "postPhotoKeys"
    }
}

/** 글 카테고리. 연결이 없으면 빈 값으로 덮지 않고 키를 비워 리졸버가 실패하게 둔다. */
@DgsDataLoader(name = PostCategoryDataLoader.NAME)
@Component
class PostCategoryDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, TargetCategory> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, TargetCategory>> = CompletableFuture.completedFuture(communityQueryUseCase.postCategories(postIds.toList()))

    companion object {
        const val NAME = "postCategory"
    }
}

/** 글에 연결된 장소 id. 키가 없으면 장소 없는 글이다. */
@DgsDataLoader(name = PostCatalogIdDataLoader.NAME)
@Component
class PostCatalogIdDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, Long> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, Long>> = CompletableFuture.completedFuture(communityQueryUseCase.postCatalogIds(postIds.toList()))

    companion object {
        const val NAME = "postCatalogId"
    }
}

/** 뷰어가 좋아요한 글인지. 뷰어는 keyContext로 받는다(SPEC-0032 R27). */
@DgsDataLoader(name = PostLikedDataLoader.NAME)
@Component
class PostLikedDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoaderWithContext<Long, Boolean> {

    override fun load(
        postIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, Boolean>> {
        val memberId = environment.requireViewer("PostLikedDataLoader")
        val liked = communityQueryUseCase.likedPostIds(postIds.toList(), memberId)
        return CompletableFuture.completedFuture(postIds.associateWith { it in liked })
    }

    companion object {
        const val NAME = "postLiked"
    }
}
