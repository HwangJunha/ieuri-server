package org.around.ieuri.tourism.application.footprint.provided.dto

/** 발급된 공유 링크. 토큰이 아니라 서버가 조립한 완성 URL이다. */
data class MapShareLinkView(
    val shareUrl: String,
    val createdAt: java.time.Instant,
)
