package org.around.ieuri.tourism.application.spot.required

/** spot 이벤트 발행 포트. 상세를 받아 온 사실을 write-behind 적재로 넘긴다. */
interface SpotEventPublisherPort {
    fun publish(event: Any)
}
