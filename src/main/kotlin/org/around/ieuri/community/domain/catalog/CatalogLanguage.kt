package org.around.ieuri.community.domain.catalog

import org.around.ieuri.community.domain.shared.ContentLanguage

/** 한 카탈로그가 한 언어에서 해소된다는 사실. 원본은 tourism에 있고 커뮤니티는 사본을 든다. */
data class CatalogLanguage(
    val catalogId: Long,
    val language: ContentLanguage,
)
