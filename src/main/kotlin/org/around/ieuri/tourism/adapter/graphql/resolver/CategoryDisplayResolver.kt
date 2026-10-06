package org.around.ieuri.tourism.adapter.graphql.resolver

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import org.around.ieuri.tourism.adapter.graphql.dataloader.CategoryDisplayDataLoader
import org.around.ieuri.tourism.adapter.graphql.support.domainLocale
import org.around.ieuri.tourism.application.shared.provided.dto.CategoryDisplay
import org.dataloader.DataLoader
import java.util.concurrent.CompletableFuture
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * `SpotCategory`의 표시 필드(SPEC-0020). 요청된 경우에만 조회한다 — `{ category { code } }`만 물으면 읽지 않는다.
 * - 조회는 [CategoryDisplayDataLoader]가 모아 한 번에 한다.
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
