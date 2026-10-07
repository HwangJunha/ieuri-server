package org.around.ieuri.tourism.domain.catalog

import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 카탈로그의 한 언어 표시값 — 제목과 주소. 이 언어에 원본이 존재한다는 증거다(SPEC-0029 R-6).
 * - 값은 전부 업스트림 원문이다. [address]는 원문에 없으면 null이다.
 * - [visible]이 false면 그 언어의 원본이 사라져 조회에서 감춘 것이다. 행은 지우지 않는다.
 */
data class CatalogTranslation(
    val language: DomainLocale,
    val title: String,
    val address: String?,
    val visible: Boolean,
) {
    init {
        require(title.isNotBlank()) { "제목은 비어 있을 수 없습니다." }
    }
}
