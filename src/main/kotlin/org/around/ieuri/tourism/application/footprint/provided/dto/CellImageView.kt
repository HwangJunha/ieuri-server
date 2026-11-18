package org.around.ieuri.tourism.application.footprint.provided.dto

import org.around.ieuri.tourism.domain.footprint.CellImageKind
import org.around.ieuri.tourism.domain.shared.CopyrightType

/** 칸의 대표 이미지. [attributionText]는 [copyright]에서 파생된다. */
data class CellImageView(
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String,
    val kind: CellImageKind,
    val copyright: CopyrightType,
) {
    val attributionText: String get() = copyright.attributionText
}
