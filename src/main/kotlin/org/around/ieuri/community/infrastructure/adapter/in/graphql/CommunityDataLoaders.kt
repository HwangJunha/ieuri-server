package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.port.out.client.MemberLookupPort
import org.around.ieuri.community.application.port.out.client.SpotLookupPort
import org.around.ieuri.community.application.query.PostAuthorView
import org.around.ieuri.community.application.query.PostSpotView
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 장소(spot) 배치 로더. 피드 한 페이지의 대상 id를 모아 **한 번에** 조회한다.
 *
 * 없으면 카드 20장에 spot 조회가 20번 붙는다(N+1). GraphQL의 필드 리졸버는 카드마다
 * 호출되므로, 배치하지 않으면 지연 조회의 이점이 N+1로 상쇄된다.
 *
 * **활성 언어를 컨텍스트로 받는다**(SPEC-0029 R-24). 언어는 요청당 하나이므로 키는 여전히
 * 카탈로그 id 하나지만, 언어 없이 조회하면 한국어 고정으로 돌아간다 — 그래서
 * [MappedBatchLoaderWithContext]다. 리졸버가 `keyContext`로 언어를 실어 보낸다.
 *
 * 한 배치의 키는 전부 같은 요청에서 오므로 언어도 하나다 — 그래서 아무 keyContext나 집어
 * 써도 된다. 없으면 **폴백하지 않고 실패한다**: 요청이 언제나 갖는 값이라 부재는 결함이다.
 *
 * **해소되지 않는 id는 결과에 키가 없다** — 호출자는 그것을 "이 언어에 없는 장소"로 다룬다.
 */
@DgsDataLoader(name = PostSpotDataLoader.NAME)
@Component
class PostSpotDataLoader(
    private val spotLookupPort: SpotLookupPort,
) : MappedBatchLoaderWithContext<Long, PostSpotView> {

    override fun load(
        catalogIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, PostSpotView>> {
        // **폴백하지 않는다.** 언어는 요청이 언제나 갖는 값이라, 없다는 것은 리졸버가
        // keyContext를 빠뜨렸다는 뜻이다. 기본 언어로 흘리면 JA 사용자에게 EN 제목이
        // 조용히 나가고, `questionSpot`의 requireNotNull이 그 오류를 오히려 덮는다.
        val language = environment.keyContexts.values
            .filterIsInstance<ContentLanguage>()
            .firstOrNull()
            ?: error("PostSpotDataLoader는 keyContext로 언어를 받아야 한다 — 리졸버가 load(key, language)로 부른다.")

        return CompletableFuture.completedFuture(
            spotLookupPort.findSummaries(catalogIds.toList(), language),
        )
    }

    companion object {
        const val NAME = "postSpot"
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

    override fun load(memberIds: Set<Long>): CompletionStage<Map<Long, PostAuthorView>> = CompletableFuture.completedFuture(memberLookupPort.findAuthors(memberIds.toList()))

    companion object {
        const val NAME = "postAuthor"
    }
}
