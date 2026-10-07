package org.around.ieuri.community.application.post.required.dto

/** 반경 조회의 중심과 거리. 상한 절삭은 애플리케이션이 이미 마친 상태로 온다. */
data class PostsArea(
    val longitude: Double,
    val latitude: Double,
    val radiusMeters: Int,
)
