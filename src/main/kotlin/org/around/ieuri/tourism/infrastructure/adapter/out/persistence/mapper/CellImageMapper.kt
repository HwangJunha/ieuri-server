package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.tourism.domain.vo.CellImageKind
import org.around.ieuri.tourism.application.query.CellImageView
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellImageEntity

/**
 * `map_cell_image` 한 행의 원시 값. 어댑터가 `ResultSet`에서 이 형태로 꺼내고 변환은
 * [toCellImageView]가 맡는다.
 *
 * **`ResultSet` 확장으로 두지 않은 이유는 그러면 단위 테스트가 DB를 요구하기 때문이다** —
 * 변환은 순수 함수여야 필드 단위로 검증할 수 있다(POL-033).
 */
data class CellImageRow(
    val cellCode: String,
    val imageUrl: String,
    val thumbnailUrl: String?,
    val caption: String,
    val kind: String,
    val copyright: String,
)

/**
 * 저장 행 → 읽기 모델(POL-033).
 *
 * **`attributionText`를 저장 행에서 읽지 않는다** — [CopyrightType]이 그 문구를 소유하며,
 * 적재해 두면 둘이 어긋날 수 있다(기존 `SpotImage`의 규약과 같다).
 *
 * `kind`·`copyright`는 DB CHECK 제약이 값을 강제하므로 알 수 없는 값은 적재 단계에서 막힌다.
 */
fun CellImageRow.toCellImageView(): CellImageView {
    val copyrightType = CopyrightType.valueOf(copyright)
    return CellImageView(
        imageUrl = imageUrl,
        thumbnailUrl = thumbnailUrl,
        caption = caption,
        kind = CellImageKind.valueOf(kind),
        copyright = copyrightType,
        attributionText = copyrightType.attributionText,
    )
}

/**
 * 엔티티 → 읽기 모델(POL-033).
 *
 * `attributionText`는 [CopyrightType]에서 파생된다 — 컬럼이 없는 이유이기도 하다.
 */
fun MapCellImageEntity.toCellImageView(): CellImageView = CellImageView(
    imageUrl = imageUrl,
    thumbnailUrl = thumbnailUrl,
    caption = caption,
    kind = kind,
    copyright = copyright,
    attributionText = copyright.attributionText,
)
