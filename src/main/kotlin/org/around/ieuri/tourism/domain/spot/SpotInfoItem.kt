package org.around.ieuri.tourism.domain.spot

/** 이용안내 1건(항목명/내용 쌍). 둘 다 비어 있을 수 없다. */
data class SpotInfoItem(
    val name: String,
    val text: String,
) {
    init {
        require(name.isNotBlank()) { "이용안내 항목명은 비어 있을 수 없습니다." }
        require(text.isNotBlank()) { "이용안내 항목 내용은 비어 있을 수 없습니다." }
    }
}
