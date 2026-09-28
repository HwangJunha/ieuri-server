package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.community.application.port.out.client.CategoryConfigPort
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toChipViewOrNull
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.springframework.stereotype.Component

/**
 * 카테고리 구성 조회. `tourism`의 **인바운드 포트**를 통해서만 접근한다 — [SpotLookupAdapter]와
 * 같은 이유로, 그래야 tourism이 커뮤니티를 계속 모른 채로 있을 수 있다.
 *
 * 이 어댑터가 존재하는 이유는 `community_chip` 폐기다(SPEC-0020, V57). 앱별 카테고리 구성이
 * `app_category` 한 곳으로 합쳐져 관광지 목록과 커뮤니티 칩이 같은 표를 본다.
 */
@Component
class CategoryConfigAdapter(
    private val spotQueryUseCase: SpotQueryUseCase,
) : CategoryConfigPort {

    /** 언어·카테고리를 문자열로 주고받는다 — tourism의 도메인 타입을 이 도메인이 알지 않는다. */
    override fun findCategories(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> = spotQueryUseCase
        .availableCategoryCodes(appKey, language.name)
        .mapNotNull { it.toChipViewOrNull() }
}
