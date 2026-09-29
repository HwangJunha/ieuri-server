package org.around.ieuri.community.domain.vo

/** 피드 정렬 방식. 무엇이 보이는지까지 함께 정한다. */
enum class PostSortOrder {
    /** 최신순. 좌표 반경으로 좁힌 경우에도 장소 없는 글이 함께 나온다. */
    LATEST,

    /**
     * 가까운 순. 반경 안 spot에 붙은 글만 나오며 장소 없는 글은 빠진다.
     * - 좌표를 주지 않으면 [LATEST]와 같게 동작한다.
     */
    DISTANCE,
}
