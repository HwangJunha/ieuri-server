package org.around.ieuri.community.application.port.out.client

import org.around.ieuri.common.context.AppKey
import org.around.ieuri.community.application.query.CommunityChipView
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 앱·언어별 카테고리 구성 조회(SPEC-0020). 상단 칩이 이 구성을 그대로 그린다.
 *
 * community가 이 표를 소유하지 않는다 — 같은 축을 관광지 목록도 쓰므로 한 곳에서만
 * 관리되어야 한다. 이 포트 뒤에 어느 도메인이 있는지는 application이 알지 않는다.
 */
interface CategoryConfigPort {
    /** 이 앱·이 언어의 켜진 카테고리를 순서대로. 구성이 없으면 빈 목록이며 오류가 아니다. */
    fun findCategories(appKey: AppKey, language: ContentLanguage): List<CommunityChipView>
}
