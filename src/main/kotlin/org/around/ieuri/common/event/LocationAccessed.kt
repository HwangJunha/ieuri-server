package org.around.ieuri.common.event

/**
 * 좌표를 인자로 받는 연산이 호출됐다(SPEC-0032 R19, POL-057).
 * - 좌표·행정구역 어느 단위도 담지 않는다(POL-045). 비로그인은 발행하지 않는다.
 */
data class LocationAccessed(
    val memberId: Long,
    val operation: String,
)
