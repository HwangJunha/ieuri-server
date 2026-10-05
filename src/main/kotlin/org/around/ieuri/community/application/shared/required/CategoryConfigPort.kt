package org.around.ieuri.community.application.shared.required

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.community.application.shared.dto.CommunityChipView
import org.around.ieuri.community.domain.shared.ContentLanguage

/** 앱·언어별 카테고리 구성 조회(SPEC-0020). 상단 칩이 이 구성을 그대로 그린다. */
interface CategoryConfigPort {
    /** 이 앱·이 언어의 켜진 카테고리를 순서대로. 구성이 없으면 빈 목록이며 오류가 아니다. */
    fun findCategories(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}
