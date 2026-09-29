package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellImageEntity

/** 엔티티 → 읽기 모델(POL-033). `attributionText`는 [CopyrightType]에서 파생된다. */
fun MapCellImageEntity.toCellImageView(): CellImageView = CellImageView(
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    caption = caption,
    kind = kind,
    copyright = copyright,
    attributionText = copyright.attributionText,
)
