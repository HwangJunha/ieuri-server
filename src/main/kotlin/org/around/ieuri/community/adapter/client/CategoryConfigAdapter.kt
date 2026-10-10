package org.around.ieuri.community.adapter.client

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.community.adapter.client.mapper.toChipViewOrNull
import org.around.ieuri.community.application.post.provided.dto.CommunityChipView
import org.around.ieuri.community.application.post.required.CategoryConfigPort
import org.around.ieuri.community.domain.shared.ContentLanguage
import org.around.ieuri.tourism.application.category.provided.CategoryQueryUseCase
import org.springframework.stereotype.Component

/** tourism의 인바운드 포트로 앱별 카테고리 구성(`app_category`)을 조회한다(SPEC-0020). */
@Component
class CategoryConfigAdapter(
    private val spotQueryUseCase: CategoryQueryUseCase,
) : CategoryConfigPort {

    override fun findCategories(appKey: AppKey, language: ContentLanguage): List<CommunityChipView> = spotQueryUseCase
        .availableCategoryCodes(appKey, language.name)
        .mapNotNull { it.toChipViewOrNull() }
}
