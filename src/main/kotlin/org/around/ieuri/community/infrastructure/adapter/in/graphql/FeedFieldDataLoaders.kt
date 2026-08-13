package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.FeedQueryUseCase
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 피드의 지연 필드 배치 로더(SPEC-0017). 사진·좋아요 여부는 각각 다른 테이블에 있어
 * **피드 목록과 함께 미리 읽지 않는다** — 클라이언트가 그 필드를 골랐을 때만 돌고, 그때도
 * 페이지 전체를 한 번의 쿼리로 읽는다(PostFieldDataLoaders와 같은 구조).
 */

@DgsDataLoader(name = FeedItemPhotoKeysDataLoader.NAME)
@Component
class FeedItemPhotoKeysDataLoader(
    private val feedQueryUseCase: FeedQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(feedItemIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = feedQueryUseCase.feedItemPhotoKeys(feedItemIds.toList())
        // 피드는 사진이 필수라 키가 없으면 데이터가 깨진 것이다. 다만 빈 목록으로 채워
        // 조회 전체를 실패시키지는 않는다 — 한 건의 손상이 페이지를 통째로 죽이지 않게 한다.
        return CompletableFuture.completedFuture(feedItemIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "feedItemPhotoKeys"
    }
}

/** 배치 안의 모든 피드가 같은 호출자의 요청이므로 회원을 한 번만 읽는다. */
@DgsDataLoader(name = FeedItemLikedDataLoader.NAME)
@Component
class FeedItemLikedDataLoader(
    private val feedQueryUseCase: FeedQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) : MappedBatchLoader<Long, Boolean> {

    override fun load(feedItemIds: Set<Long>): CompletionStage<Map<Long, Boolean>> {
        val memberId = currentMemberProvider.requireMemberId()
        val liked = feedQueryUseCase.likedFeedItemIds(feedItemIds.toList(), memberId)
        return CompletableFuture.completedFuture(feedItemIds.associateWith { it in liked })
    }

    companion object {
        const val NAME = "feedItemLiked"
    }
}
