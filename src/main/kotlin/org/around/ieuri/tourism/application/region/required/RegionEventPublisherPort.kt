package org.around.ieuri.tourism.application.region.required

/** 지역 추천 이벤트 발행 포트. 좌표 조회 사실을 알린다. */
interface RegionEventPublisherPort {
    fun publish(event: Any)
}
