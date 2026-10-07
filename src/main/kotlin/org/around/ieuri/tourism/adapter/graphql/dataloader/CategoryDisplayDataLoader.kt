package org.around.ieuri.tourism.adapter.graphql.dataloader

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.tourism.application.category.provided.CategoryQueryUseCase
import org.around.ieuri.tourism.application.category.provided.dto.CategoryDisplay
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 카테고리 표시 정보의 배치 로더(SPEC-0020). 키를 모아 요청당 한 번의 `IN` 조회로 읽는다.
 * - 언어를 keyContext로 받는다(POL-039). 로더가 실행 단위로 새로 만들어지므로 한 실행의 언어는 하나다.
 * - keyContext가 없으면 폴백하지 않고 실패한다.
 * - 정의가 없는 코드도 결과에 키가 있다(코드 자체가 라벨).
 * - 루트가 읽어 둔 값은 `prime()`으로 심어 조회를 건너뛴다.
 */
@DgsDataLoader
@Component
class CategoryDisplayDataLoader(
    private val categoryQueryUseCase: CategoryQueryUseCase,
) : MappedBatchLoaderWithContext<String, CategoryDisplay> {

    override fun load(
        codes: Set<String>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<String, CategoryDisplay>> {
        val locale = environment.keyContexts.values
            .filterIsInstance<DomainLocale>()
            .firstOrNull()
            ?: error("CategoryDisplayDataLoader는 keyContext로 언어를 받아야 한다 — 리졸버가 load(code, locale)로 부른다.")

        return CompletableFuture.completedFuture(categoryQueryUseCase.categoryDisplays(codes, locale))
    }
}
