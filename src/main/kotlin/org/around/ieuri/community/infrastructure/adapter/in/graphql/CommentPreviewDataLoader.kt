package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.port.`in`.query.CommunityQueryUseCase
import org.around.ieuri.community.application.query.CommentView
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
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
 * **뷰어를 keyContext로 받는다**(SPEC-0032 R27) — 배치 디스패치는 리졸버와 다른 스레드에서
 * 돌 수 있고, 그때 ThreadLocal 기반 신원은 사라진다. 리졸버가 자기 스레드에서 확정해 싣는다.
 */
@DgsDataLoader(name = CommentPreviewDataLoader.NAME)
@Component
class CommentPreviewDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<CommentView>> {

    override fun load(
        postIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<CommentView>>> {
        val memberId = environment.requireViewer("CommentPreviewDataLoader")
        val previews = communityQueryUseCase.commentPreviews(postIds.toList(), memberId)
        // 댓글이 없는 글은 조회 결과에 키가 없다 — 빈 목록으로 채워야 리졸버가 null을 만나지 않는다.
        val complete = postIds.associateWith { previews[it].orEmpty() }
        return CompletableFuture.completedFuture(complete)
    }

    companion object {
        const val NAME = "commentPreview"
    }
}

/**
 * 대댓글 배치 로더. 최상위 댓글 id를 모아 **한 번에** 조회한다.
 *
 * 없으면 댓글 20개짜리 목록에 대댓글 쿼리가 20번 붙는다(N+1). 미리보기 로더와 같은 형태다.
 *
 * 대댓글의 replies는 항상 빈 목록이다 — 깊이가 2단계라 답글에는 답글이 달리지 않는다.
 * 조회 자체는 막지 않는다(결과가 비어 있을 뿐이라 분기를 더할 이유가 없다).
 */
@DgsDataLoader(name = CommentReplyDataLoader.NAME)
@Component
class CommentReplyDataLoader(
    private val communityQueryUseCase: CommunityQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<CommentView>> {

    override fun load(
        parentCommentIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<CommentView>>> {
        val memberId = environment.requireViewer("CommentReplyDataLoader")
        val replies = communityQueryUseCase.commentReplies(parentCommentIds.toList(), memberId)
        // 답글이 없는 댓글은 결과에 키가 없다 — 빈 목록으로 채워야 리졸버가 null을 만나지 않는다.
        val complete = parentCommentIds.associateWith { replies[it].orEmpty() }
        return CompletableFuture.completedFuture(complete)
    }

    companion object {
        const val NAME = "commentReply"
    }
}
