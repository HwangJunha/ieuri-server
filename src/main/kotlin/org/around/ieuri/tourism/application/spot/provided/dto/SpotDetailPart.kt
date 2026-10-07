package org.around.ieuri.tourism.application.spot.provided.dto

/** 상세의 네 갈래. TourAPI 오퍼레이션 하나와 우리 표 하나에 각각 대응한다. */
enum class SpotDetailPart {
    /** detailCommon2 → `spot_detail` */
    BODY,

    /** detailImage2 → `spot_detail_image` */
    GALLERY,

    /** detailIntro2 → `spot_detail_intro` */
    INTRO,

    /** detailInfo2 → `spot_detail_info` */
    INFO,
}
