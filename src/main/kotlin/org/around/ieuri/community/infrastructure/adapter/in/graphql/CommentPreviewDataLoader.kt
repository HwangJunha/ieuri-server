package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.query.CommentView
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 댓글 미리보기 배치 로더. 피드 한 페이지의 글 id를 모아 **한 번에** 조회한다.
 *
 * 없으면 카드 20장에 댓글 쿼리가 20번 붙는다(N+1). 지연 필드는 요청당 과다 조회를 막고
 * DataLoader는 요청 **안**의 N+1을 막는 것이라, 둘은 다른 문제이며 둘 다 필요하다.
 *
 * 페이지네이션은 여기서 하지 않는다 — 목록 안의 목록에 커서를 두면 화면마다 커서 관리가
 * 갈린다. 전체 목록과 더 보기는 루트의 `comments(postId:)`가 담당한다.
 *
 * 배치 안의 모든 글이 같은 호출자의 요청이므로 회원을 한 번만 읽는다.
 */
@DgsDataLoader(name = CommentPreviewDataLoader.NAME)
@Component
class CommentPreviewDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) : MappedBatchLoader<Long, List<CommentView>> {

    override fun load(postIds: Set<Long>): CompletionStage<Map<Long, List<CommentView>>> {
        val memberId = currentMemberProvider.requireMemberId()
        val previews = communityQueryUseCase.commentPreviews(postIds.toList(), memberId)
        // 댓글이 없는 글은 조회 결과에 키가 없다 — 빈 목록으로 채워야 리졸버가 null을 만나지 않는다.
        val complete = postIds.associateWith { previews[it].orEmpty() }
        return CompletableFuture.completedFuture(complete)
    }

    companion object {
        const val NAME = "commentPreview"
    }
}
