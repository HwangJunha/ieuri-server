package org.around.ieuri.tourism.domain.spot

/** 수집 집합을 믿을 수 없어 사라진 원본 정리를 건너뛴다. [reason]은 배치 실행 이력에 남는다. */
class SweepBlockedException(val reason: String) : RuntimeException("spot sweep blocked: $reason")
