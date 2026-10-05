package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.post.SpotPoint

/** 글이 가리키는 장소. 카탈로그 id(언어별 `spot.id`가 아니다)와 적재 시 복사한 좌표는 함께 있다. */
data class PostSpot(val catalogId: Long, val point: SpotPoint) {
    init {
        require(catalogId > 0) { "catalogId는 양수여야 합니다." }
    }
}
