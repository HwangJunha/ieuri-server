package org.around.ieuri.community.adapter.config

import org.springframework.boot.context.properties.ConfigurationProperties

/** 커뮤니티 조회·피드 설정. */
@ConfigurationProperties(prefix = "ieuri.community")
data class CommunityProperties(
    /** 커넥션 first 상한. 초과 요청은 오류가 아니라 절삭된다. */
    val maxPageSize: Int = 50,
    /** 피드 카드에 얹는 댓글 미리보기 개수. */
    val commentPreviewSize: Int = 3,
    val maxRadiusMeters: Int = 100_000,
)
