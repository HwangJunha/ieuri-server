package org.around.ieuri.tourism.application.query

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.MapCell

/**
 * 칸 도메인 모델 → 읽기 모델(SPEC-0030).
 *
 * 조회와 쓰기 서비스가 **같은 변환을 써야 한다** — 뮤테이션이 돌려주는 칸과 조회가 돌려주는
 * 칸이 다르게 보이면 클라이언트가 캐시를 고칠 수 없다. 그래서 어느 한 서비스의 private 함수가
 * 아니라 여기 둔다.
 *
 * 다국어 해소(활성 언어 → EN → KO)는 도메인의 `AreaName.resolve`가 하고 여기서는 부르기만 한다.
 */
fun MapCell.toView(locale: DomainLocale, visitedDistrictCodes: Set<String>): MapCellView = MapCellView(
    code = code,
    districtCodes = districtCodes.sorted(),
    name = name.resolve(locale),
    provinceName = provinceName.resolve(locale),
    provinceCode = provinceCode,
    center = center,
    viewerVisited = isVisitedBy(visitedDistrictCodes),
)
