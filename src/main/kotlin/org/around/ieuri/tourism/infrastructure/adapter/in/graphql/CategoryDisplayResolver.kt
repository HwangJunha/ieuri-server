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
    fun label(dfe: DgsDataFetchingEnvironment): CompletableFuture<String> {
        val loader: DataLoader<String, CategoryDisplay> =
            dfe.getDataLoader(CategoryDisplayDataLoader::class.java)
        val category = requireNotNull(dfe.getSource<GqlSpotCategory>())
        return loader.load(category.code, dfe.domainLocale()).thenApply { it.label }
    }

    @DgsData(parentType = "SpotCategory", field = "iconUrl")
    fun iconUrl(dfe: DgsDataFetchingEnvironment): CompletableFuture<String?> {
        val loader: DataLoader<String, CategoryDisplay> =
            dfe.getDataLoader(CategoryDisplayDataLoader::class.java)
        val category = requireNotNull(dfe.getSource<GqlSpotCategory>())
        return loader.load(category.code, dfe.domainLocale()).thenApply { it.iconUrl }
    }
}
