package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.lookup.provided.MemberLookupUseCase
import org.around.ieuri.community.domain.lookup.MemberSummary
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** 작성자 배치 로더. 글·댓글 등이 공유한다. 없는 회원(탈퇴)은 키가 없고 리졸버가 대체 표기로 채운다. */
@DgsDataLoader(name = PostAuthorDataLoader.NAME)
@Component
class PostAuthorDataLoader(
    private val memberLookup: MemberLookupUseCase,
) : MappedBatchLoader<Long, MemberSummary> {

    override fun load(memberIds: Set<Long>): CompletionStage<Map<Long, MemberSummary>> = CompletableFuture.completedFuture(memberLookup.members(memberIds.toList()))

    companion object {
        const val NAME = "postAuthor"
    }
}
