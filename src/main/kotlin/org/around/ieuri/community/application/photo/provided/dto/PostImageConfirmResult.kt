package org.around.ieuri.community.application.photo.provided.dto

/** 확인 결과. 장별로 성패가 갈린다 — 한 장이 실패해도 나머지는 확정된다. */
data class PostImageConfirmResult(
    val confirmed: List<String>,
    val failed: List<PostImageConfirmFailure>,
)
