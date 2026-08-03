package org.around.ieuri.tourism.application.command

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.domain.vo.FestivalPeriod

/**
 * 축제 1건의 적재 입력(SPEC-0007 R-1). [SyncSpotCommand]와 같은 자리·같은 성격이다 — 수집 포트
 * (`FestivalSyncPort`)가 만들고 적재 포트(`FestivalStorePort`)가 소비하므로 어느 쪽도 소유하지 않는다.
 *
 * **제목·주소·좌표 등 관광지 공통 필드를 담지 않는다**(AC-3). 그 값은 같은 `(contentId, language)`의
 * `spot` 행에 이미 있고, 축제 적재가 그것을 다시 쓰면 두 경로가 같은 컬럼을 놓고 경합한다.
 * 여기 있는 것은 `searchFestival2`만 주는 축제 전용 정보뿐이다.
 *
 * [progressType]·[festivalType]은 **원문 문자열 그대로**다(R-5). 공식 코드 체계가 없어 enum으로
 * 만들지 않으며(POL-023), 빈 값은 어댑터가 null로 정규화해 넘긴다.
 */
data class SyncFestivalCommand(
    val contentId: String,
    val language: DomainLocale,
    val period: FestivalPeriod,
    val progressType: String?,
    val festivalType: String?,
) {
    init {
        // AC-4. contentId가 없으면 spot의 어느 행에도 연결할 수 없다 — FK 위반으로 적재 시점에
        // 터지기 전에 조립 자체를 거부한다.
        require(contentId.isNotBlank()) { "contentId는 필수입니다." }
    }
}
