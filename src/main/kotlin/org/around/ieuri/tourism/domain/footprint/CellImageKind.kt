package org.around.ieuri.tourism.domain.footprint

/** 칸 대표 이미지의 성격(SPEC-0030 R9). 저작권 유형([CopyrightType])과는 무관하다. */
enum class CellImageKind {
    /** 랜드마크 — 문화재, 자연경관, 공원, 구조물 등 그 지역을 상징하는 장소. */
    LANDMARK,

    /** 특산품 — 그 지역을 대표하는 농수축산물이나 가공품. */
    SPECIALTY,
}
