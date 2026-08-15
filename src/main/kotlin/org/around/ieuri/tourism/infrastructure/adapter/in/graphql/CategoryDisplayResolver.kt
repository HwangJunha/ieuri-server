package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.around.ieuri.tourism.application.query.CategoryDisplay
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.infrastructure.locale.domainLocale
import java.util.concurrent.ConcurrentHashMap
import org.around.ieuri.common.graphql.generated.types.SpotCategory as GqlSpotCategory

/**
 * `SpotCategory`의 표시 필드(SPEC-0020). 새 카테고리가 생겨도 앱 업데이트 없이 이름과 아이콘이
 * 나가게 하는 지점이다.
 *
 * **요청된 경우에만 조회한다** — `{ category { code } }`만 물으면 카테고리 테이블을 읽지 않는다.
 * 루트 리졸버는 코드만 채워 넘기고 표시 문구·아이콘은 여기서 붙인다.
 *
 * 한 응답에 같은 카테고리가 수십 번 나오므로(카드 100장에 카테고리는 몇 종류뿐) 요청 컨텍스트에
 * 코드별로 접어 둔다 — 없으면 카드마다 조회가 붙는다(N+1).
 */
@DgsComponent
class CategoryDisplayResolver(
    private val spotQueryUseCase: SpotQueryUseCase,
) {

    @DgsData(parentType = "SpotCategory", field = "label")
    fun label(dfe: DgsDataFetchingEnvironment): String =
        display(dfe, dfe.getSource<GqlSpotCategory>()!!.code).label

    @DgsData(parentType = "SpotCategory", field = "iconUrl")
    fun iconUrl(dfe: DgsDataFetchingEnvironment): String? =
        display(dfe, dfe.getSource<GqlSpotCategory>()!!.code).iconUrl

    /**
     * 요청 안에서 코드당 한 번만 읽는다. 정의가 없는 코드는 코드 자체가 라벨이 된다.
     *
     * **ConcurrentHashMap이어야 한다** — 가상 스레드가 켜져 있어 형제 필드가 서로 다른 스레드에서
     * 동시에 이 맵을 건드린다. 일반 HashMap이면 리사이즈 중 항목이 유실되거나 잘못된 값이 나오며,
     * 부하에서만 간헐적으로 깨져 단위 테스트로는 드러나지 않는다.
     */
    private fun display(dfe: DgsDataFetchingEnvironment, code: String): CategoryDisplay {
        @Suppress("UNCHECKED_CAST")
        val cache = dfe.graphQlContext.computeIfAbsent(CACHE_KEY) {
            ConcurrentHashMap<String, CategoryDisplay>()
        } as ConcurrentHashMap<String, CategoryDisplay>

        return cache.computeIfAbsent(code) {
            spotQueryUseCase.categoryDisplays(setOf(it), dfe.domainLocale())[it]
                ?: CategoryDisplay.fallback(SpotCategory(it))
        }
    }

    companion object {
        private const val CACHE_KEY = "tourism.categoryDisplay"

        /**
         * 이미 읽어 둔 표시 정보를 요청 메모에 심는다. 루트가 구성을 조회하며 라벨·아이콘을 함께
         * 받아 왔다면 필드 리졸버가 그것을 다시 묻지 않게 하기 위한 것이다 — 없으면 칩 8개에
         * 조회가 8번 더 붙는다.
         */
        @Suppress("UNCHECKED_CAST")
        fun seed(dfe: DgsDataFetchingEnvironment, displays: Collection<CategoryDisplay>) {
            if (displays.isEmpty()) return
            val cache = dfe.graphQlContext.computeIfAbsent(CACHE_KEY) {
                ConcurrentHashMap<String, CategoryDisplay>()
            } as ConcurrentHashMap<String, CategoryDisplay>
            displays.forEach { cache.putIfAbsent(it.category.code, it) }
        }
    }
}
