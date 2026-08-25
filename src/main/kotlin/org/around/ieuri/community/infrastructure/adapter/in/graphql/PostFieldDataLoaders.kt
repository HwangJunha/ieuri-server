package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.domain.vo.TargetCategory
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 글의 지연 필드 배치 로더들. 태그·사진·카테고리·장소·좋아요는 각각 다른 테이블에 있어
 * **글 목록과 함께 미리 읽지 않는다** — 클라이언트가 그 필드를 골랐을 때만 이 로더가 돌고,
 * 그때도 페이지 전체를 한 번의 쿼리로 읽는다.
 *
 * 두 문제를 함께 푸는 구조다: 지연 필드가 **요청당 과다 조회**를 막고, DataLoader가 요청
 * **안의 N+1**을 막는다. 하나만 해서는 둘 중 하나가 남는다.
 *
 * 값이 없는 글은 조회 결과에 키가 없으므로, 각 로더가 빈 값으로 채워 돌려준다 —
 * MappedBatchLoader가 키를 빠뜨리면 리졸버가 null을 만난다.
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

/**
 * 카테고리는 모든 글에 있다. 없으면 연결이 깨진 것이라 **빈 값으로 덮지 않고** 키를 비운다 —
 * 리졸버가 그 사실을 드러내야 조용히 잘못된 카테고리가 표시되지 않는다.
 */
@DgsDataLoader(name = PostCategoryDataLoader.NAME)
@Component
class PostCategoryDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, TargetCategory> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, TargetCategory>> =
        CompletableFuture.completedFuture(communityQueryUseCase.postCategories(postIds.toList()))

    companion object {
        const val NAME = "postCategory"
    }
}

/**
 * 장소는 **선택이라 없는 글이 정상이다.** 없는 글의 키를 비워 두면 리졸버가 null을 받고,
 * 그것이 곧 "장소 없는 글"이라는 뜻이다.
 */
@DgsDataLoader(name = PostCatalogIdDataLoader.NAME)
@Component
class PostCatalogIdDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoader<Long, Long> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, Long>> =
        CompletableFuture.completedFuture(communityQueryUseCase.postCatalogIds(postIds.toList()))

    companion object {
        const val NAME = "postCatalogId"
    }
}

/**
 * 배치 안의 모든 글이 같은 호출자의 요청이므로 회원을 한 번만 읽는다.
 *
 * **뷰어를 keyContext로 받는다**(SPEC-0032 R27) — 배치 디스패치가 다른 스레드에서 돌면
 * ThreadLocal 기반 신원이 사라져, 좋아요 표시가 통째로 뒤집히거나 인증 오류가 난다.
 */
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
