package org.around.ieuri.tourism.domain.vo

/**
 * 칸 대표 이미지의 성격(SPEC-0030 R9).
 *
 * 클라이언트가 배지나 라벨로 구분하는 값이며, 저작권 유형([CopyrightType])과는 무관하다 —
 * 랜드마크든 특산품이든 저작권은 이미지별로 따로 붙는다.
 */
enum class CellImageKind {
    /** 랜드마크 — 문화재, 자연경관, 공원, 구조물 등 그 지역을 상징하는 장소. */
    LANDMARK,

    /** 특산품 — 그 지역을 대표하는 농수축산물이나 가공품. */
    SPECIALTY,
}
