package org.around.ieuri.tourism.infrastructure.batch

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 반려동물 동반 가능 관광지 동기화 설정(SPEC-0015 · V48). 이 Step은 `spotSyncJob` 안에서 함께
 * 돌지만 설정은 별도 클래스로 둔다(축제 Step과 같은 처리).
 *
 * 언어 키가 없다 — `detailPetTour2`는 국문 서비스에만 존재한다.
 *
 * @param pageSize detailPetTour2의 numOfRows. 실측 총계 9,701건이라 1000이면 10페이지다.
 *   업스트림이 거부하면 코드 수정 없이 낮출 수 있다.
 */
@ConfigurationProperties(prefix = "ieuri.tourapi.pet-friendly-sync")
data class PetFriendlySyncProperties(
    val pageSize: Int = 1_000,
)
