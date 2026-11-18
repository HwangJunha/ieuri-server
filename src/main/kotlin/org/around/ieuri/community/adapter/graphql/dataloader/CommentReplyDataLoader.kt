package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.comment.provided.CommentQueryUseCase
import org.around.ieuri.community.application.comment.provided.dto.CommentView
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

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
