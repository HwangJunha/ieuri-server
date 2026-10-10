package org.around.ieuri.community.application.post

/** 글 조회 설정값. [maxRadiusMeters] 초과 반경은 오류가 아니라 상한으로 절삭된다. */
data class PostPolicy(val maxRadiusMeters: Int)
