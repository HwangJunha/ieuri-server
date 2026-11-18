package org.around.ieuri.tourism.application.catalog.required.dto

/** `spot.id`가 가리키는 행의 upstream 식별 정보와 카탈로그 좌표. */
data class SpotIdentity(
    /**
     * 원본 `spot.id`. 상세 캐시·`spot_detail` 계열의 키다.
     * - 카탈로그 id를 대신 쓰면 안 된다. 두 id 공간이 겹쳐 다른 장소의 본문이 적재될 수 있다.
     */
    val spotId: Long,
    val contentId: String,
    /** 경도. 원본 `spot` 행이 아니라 `spot_catalog`의 값이다. */
    val mapX: Double,
    /** 위도. 출처는 [mapX]와 같다. */
    val mapY: Double,
)
