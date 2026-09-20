package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.MappedBatchLoaderWithContext
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 카테고리 표시 정보의 배치 로더(SPEC-0020, PERF-20260921).
 *
 * 한 응답에 같은 카테고리가 수십 번 나오므로(카드 100장에 카테고리는 몇 종류뿐) 배치하지
 * 않으면 종류 수만큼 조회가 붙는다. 어댑터의 `findDisplays(codes: Set)`가 `IN`으로 한 번에
 * 읽으므로 키를 모아 주면 요청당 1회다.
 *
 * **언어를 keyContext로 받는다**(POL-039) — `category_translation`이 언어별 행이라 언어가
 * 빠지면 다국어가 섞인다. 언어는 요청당 하나이므로 키는 코드 하나로 두고
 * `PostSpotDataLoader`와 같은 방식으로 싣는다. 한 배치의 키는 전부 같은 요청에서 오므로
 * 아무 keyContext나 집어 쓴다. **폴백하지 않고 실패한다**: 요청이 언제나 갖는 값이라 부재는
 * 리졸버가 `load(code, locale)`을 빠뜨렸다는 뜻이고, 기본 언어로 흘리면 JA 사용자에게 EN
 * 라벨이 조용히 나간다.
 *
 * 정의가 없는 코드도 결과에 키가 있다 — 유스케이스가 코드 자체를 라벨로 쓰는 폴백을 채운다.
 */
@DgsDataLoader(name = CategoryDisplayDataLoader.NAME)
@Component
class CategoryDisplayDataLoader(
    private val spotQueryUseCase: SpotQueryUseCase,
) : MappedBatchLoaderWithContext<String, CategoryDisplay> {

    override fun load(
        codes: Set<String>,
        environment: BatchLoaderEnvironment,
    ): CompletionStage<Map<String, CategoryDisplay>> {
        val locale = environment.keyContexts.values
            .filterIsInstance<DomainLocale>()
            .firstOrNull()
            ?: error("CategoryDisplayDataLoader는 keyContext로 언어를 받아야 한다 — 리졸버가 load(code, locale)로 부른다.")

        return CompletableFuture.completedFuture(spotQueryUseCase.categoryDisplays(codes, locale))
    }

    companion object {
        const val NAME = "tourism.categoryDisplay"
    }
}
