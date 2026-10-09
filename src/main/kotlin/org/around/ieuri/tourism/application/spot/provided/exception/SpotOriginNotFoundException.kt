package org.around.ieuri.tourism.application.spot.provided.exception

/** 상세를 붙일 원본이 없다 — 조회와 적재 사이에 수집 정리가 지웠을 수 있다. 적재 쪽은 다음 조회가 다시 시도한다. */
class SpotOriginNotFoundException(spotId: Long) : RuntimeException("원본이 없습니다: spotId=$spotId")
