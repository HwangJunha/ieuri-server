package org.around.ieuri.community.infrastructure.adapter.out.client

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.community.application.port.out.client.CategoryConfigPort
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.domain.vo.ContentLanguage
import org.around.ieuri.community.infrastructure.adapter.out.client.mapper.toChipViewOrNull
import org.around.ieuri.tourism.application.port.`in`.query.SpotQueryUseCase
import org.springframework.stereotype.Component

/** tourism의 인바운드 포트로 앱별 카테고리 구성(`app_category`)을 조회한다(SPEC-0020). */
@Component
class CategoryConfigAdapter(
    private val spotQueryUseCase: SpotQueryUseCase,
) : CategoryConfigPort {

    override fun findCategories(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> = spotQueryUseCase
        .availableCategoryCodes(appKey, language.name)
        .mapNotNull { it.toChipViewOrNull() }
}
