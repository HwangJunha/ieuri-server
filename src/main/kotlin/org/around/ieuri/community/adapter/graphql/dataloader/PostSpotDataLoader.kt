package org.around.ieuri.community.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.community.application.lookup.provided.SpotLookupUseCase
import org.around.ieuri.community.domain.lookup.SpotSummary
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 장소(spot) 배치 로더. 페이지의 카탈로그 id를 모아 한 번에 조회한다.
 * - 활성 언어를 keyContext로 받는다(SPEC-0029 R-24). 없으면 폴백하지 않고 실패한다.
 * - 해소되지 않는 id는 결과에 키가 없다 — "이 언어에 없는 장소"다.
 */
@DgsDataLoader(name = PostSpotDataLoader.NAME)
@Component
class PostSpotDataLoader(
    private val spotLookup: SpotLookupUseCase,
) : MappedBatchLoaderWithContext<Long, SpotSummary> {

    override fun load(
        catalogIds: Set<Long>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<Long, SpotSummary>> {
        val language = environment.keyContexts.values
            .filterIsInstance<ContentLanguage>()
            .firstOrNull()
            ?: error("PostSpotDataLoader는 keyContext로 언어를 받아야 한다 — 리졸버가 load(key, language)로 부른다.")

        return CompletableFuture.completedFuture(
            spotLookup.spots(catalogIds.toList(), language),
        )
    }

    companion object {
        const val NAME = "postSpot"
    }
}
