package org.around.ieuri.tourism.application.catalog.provided.dto

/** 검색 입력 검증 실패 1건. 클라이언트는 메시지가 아니라 [code]로 분기한다. */
data class SearchUserError(
    val code: String,
    val message: String,
    val field: String?,
)
