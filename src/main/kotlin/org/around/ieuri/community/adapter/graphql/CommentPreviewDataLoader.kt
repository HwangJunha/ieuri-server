package org.around.ieuri.community.adapter.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.shared.dto.CommentView
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 댓글 미리보기 배치 로더. 페이지의 글 id를 모아 한 번에 조회하며 페이지네이션은 없다.
 * - 뷰어는 keyContext로 받는다(SPEC-0032 R27).
 */
@DgsDataLoader(name = CommentPreviewDataLoader.NAME)
@Component
class CommentPreviewDataLoader(
    private val commentQueryUseCase: CommentQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<CommentView>> {

    override fun load(
        postIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<CommentView>>> {
        val memberId = environment.requireViewer("CommentPreviewDataLoader")
        val previews = commentQueryUseCase.commentPreviews(postIds.toList(), memberId)
        // 댓글이 없는 글은 빈 목록으로 채운다.
        val complete = postIds.associateWith { previews[it].orEmpty() }
        return CompletableFuture.completedFuture(complete)
    }

    companion object {
        const val NAME = "commentPreview"
    }
}

/** 대댓글 배치 로더. 최상위 댓글 id를 모아 한 번에 조회한다. 대댓글의 replies는 항상 빈 목록이다. */
@DgsDataLoader(name = CommentReplyDataLoader.NAME)
@Component
class CommentReplyDataLoader(
    private val commentQueryUseCase: CommentQueryUseCase,
) : MappedBatchLoaderWithContext<Long, List<CommentView>> {

    override fun load(
        parentCommentIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, List<CommentView>>> {
        val memberId = environment.requireViewer("CommentReplyDataLoader")
        val replies = commentQueryUseCase.commentReplies(parentCommentIds.toList(), memberId)
        // 답글이 없는 댓글은 빈 목록으로 채운다.
        val complete = parentCommentIds.associateWith { replies[it].orEmpty() }
        return CompletableFuture.completedFuture(complete)
    }

    companion object {
        const val NAME = "commentReply"
    }
}
