package org.around.ieuri.tourism.application.spot.provided.dto

import org.around.ieuri.tourism.domain.spot.SpotIntro

/** 운영 정보를 이미 알고 있음을 나타내는 래퍼. [value]가 null이면 "표시할 값 없음"을 확인한 것이다 — 미스(래퍼 자체가 null)와 구분한다. */
data class KnownIntro(val value: SpotIntro?)
