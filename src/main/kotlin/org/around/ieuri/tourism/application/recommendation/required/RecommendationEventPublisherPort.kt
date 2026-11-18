package org.around.ieuri.tourism.application.recommendation.required

/** 추천 이벤트 발행 포트. 좌표 조회 사실을 알린다. */
interface RecommendationEventPublisherPort {
    fun publish(event: Any)
}
