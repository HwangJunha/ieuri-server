package org.around.ieuri.tourism.application.catalog.required

/** 카탈로그 이벤트 발행 포트. 언어 구성 변경과 좌표 조회 사실을 알린다. */
interface CatalogEventPublisherPort {
    fun publish(event: Any)
}
