package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import org.dataloader.DataLoader
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * `SpotCategory`의 표시 필드(SPEC-0020). 새 카테고리가 생겨도 앱 업데이트 없이 이름과 아이콘이
 * 나가게 하는 지점이다.
 *
 * **요청된 경우에만 조회한다** — `{ category { code } }`만 물으면 카테고리 테이블을 읽지 않는다.
 * 루트 리졸버는 코드만 채워 넘기고 표시 문구·아이콘은 여기서 붙인다.
 *
 * 조회를 접는 일은 [CategoryDisplayDataLoader]가 한다 — 같은 틱의 코드를 모아 한 번에 읽으므로
 * `label`과 `iconUrl`이, 그리고 카드 100장이 조회 하나를 공유한다.
 */
@DgsComponent
class CategoryDisplayResolver {

    @DgsData(parentType = "SpotCategory", field = "label")
    fun label(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> =
        dfe.display().thenApply { it.label }

    @DgsData(parentType = "SpotCategory", field = "iconUrl")
    fun iconUrl(dfe: DgsDataFetchingEnvironment): CompletableFuture<String?> =
        dfe.display().thenApply { it.iconUrl }

    /**
     * 부모가 돌려준 코드를 요청 언어와 함께 로더에 싣는다. 언어는 키가 아니라 keyContext다 —
     * 이유는 [CategoryDisplayDataLoader] 참조.
     */
    private fun DgsDataFetchingEnvironment.display(): CompletableFuture<CategoryDisplay> =
        categoryLoader().load(categorySource().code, domainLocale())

    /**
     * **이름이 어긋난 로더를 조용히 넘기지 않는다.** `getDataLoader`는 미등록 시 null을 주는데,
     * 그것을 흘리면 필드가 빈 채로 떠서 "데이터가 없음"과 구별되지 않는다.
     */
    private fun DgsDataFetchingEnvironment.categoryLoader(): DataLoader<String, CategoryDisplay> =
        getDataLoader<String, CategoryDisplay>(CategoryDisplayDataLoader.NAME)
            ?: error("DataLoader '${CategoryDisplayDataLoader.NAME}'가 등록되지 않았다.")

    /**
     * 부모가 돌려준 값. `!!` 대신 명시적으로 실패시킨다 — 나머지 전 리졸버의 규약이고,
     * 실패했을 때 어느 리졸버의 문제인지가 메시지에 남는다.
     */
    private fun DgsDataFetchingEnvironment.categorySource(): GqlSpotCategory =
        getSource<GqlSpotCategory>()
            ?: error("SpotCategory 필드 리졸버는 부모가 반환한 SpotCategory를 소스로 받아야 한다.")
}
