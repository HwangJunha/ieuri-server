package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.DataLoader
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
 * 빠지면 다국어가 섞인다. 키는 코드 하나로 두고 `PostSpotDataLoader`와 같은 방식으로 싣는다.
 *
 * **아무 keyContext나 집어 쓰는 근거는 로더가 실행(execution) 단위로 새로 만들어진다는 것이다**
 * — 레지스트리가 `ExecutionInput`마다 구성되고 한 실행의 `Accept-Language`는 하나다.
 * "요청당 하나"가 아니다: 한 HTTP 요청이 여러 실행을 담게 되면 요청 단위 가정은 참인 채로
 * 이 로더만 틀린다. 확인할 문장은 실행 단위 쪽이다.
 *
 * **폴백하지 않고 실패한다**: 요청이 언제나 갖는 값이라 부재는 리졸버가 `load(code, locale)`을
 * 빠뜨렸다는 뜻이고, 기본 언어로 흘리면 JA 사용자에게 EN 라벨이 조용히 나간다.
 *
 * 정의가 없는 코드도 결과에 키가 있다 — 유스케이스가 코드 자체를 라벨로 쓰는 폴백을 채운다.
 *
 * 루트가 이미 읽어 둔 값이 있으면 `prime()`으로 심어 조회를 아예 건너뛴다
 * (`SpotQueryResolver.availableSpotCategories`).
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

/**
 * 등록된 카테고리 로더를 꺼낸다.
 *
 * **이름이 어긋난 로더를 조용히 넘기지 않는다.** `getDataLoader`는 미등록 시 null을 주는데,
 * 그것을 흘리면 필드가 빈 채로 떠서 "데이터가 없음"과 구별되지 않는다.
 */
internal fun DataFetchingEnvironment.categoryDisplayLoader(): DataLoader<String, CategoryDisplay> =
    getDataLoader<String, CategoryDisplay>(CategoryDisplayDataLoader.NAME)
        ?: error("DataLoader '${CategoryDisplayDataLoader.NAME}'가 등록되지 않았다.")
