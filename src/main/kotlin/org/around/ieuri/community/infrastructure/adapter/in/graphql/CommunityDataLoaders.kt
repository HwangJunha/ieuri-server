package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostTargetView
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 대상(spot) 배치 로더. 피드 한 페이지의 대상 id를 모아 **한 번에** 조회한다.
 *
 * 없으면 카드 20장에 spot 조회가 20번 붙는다(N+1). GraphQL의 필드 리졸버는 카드마다
 * 호출되므로, 배치하지 않으면 지연 조회의 이점이 N+1로 상쇄된다.
 */
@DgsDataLoader(name = PostTargetDataLoader.NAME)
@Component
class PostTargetDataLoader(
    private val spotLookupPort: SpotLookupPort,
) : MappedBatchLoader<Long, PostTargetView> {

    override fun load(spotIds: Set<Long>): CompletionStage<Map<Long, PostTargetView>> =
        CompletableFuture.completedFuture(spotLookupPort.findSummaries(spotIds.toList()))

    companion object {
        const val NAME = "postTarget"
    }
}

/**
 * 작성자 배치 로더. 글과 댓글이 **같은 로더를 공유한다** — 한 화면에서 같은 사람이 글도 쓰고
 * 댓글도 달았다면 조회가 한 번으로 합쳐진다.
 *
 * 탈퇴 등으로 없는 회원은 결과에 키가 없으며, 리졸버가 대체 표기로 채운다.
 */
@DgsDataLoader(name = PostAuthorDataLoader.NAME)
@Component
class PostAuthorDataLoader(
    private val memberLookupPort: MemberLookupPort,
) : MappedBatchLoader<Long, PostAuthorView> {

    override fun load(memberIds: Set<Long>): CompletionStage<Map<Long, PostAuthorView>> =
        CompletableFuture.completedFuture(memberLookupPort.findAuthors(memberIds.toList()))

    companion object {
        const val NAME = "postAuthor"
    }
}
