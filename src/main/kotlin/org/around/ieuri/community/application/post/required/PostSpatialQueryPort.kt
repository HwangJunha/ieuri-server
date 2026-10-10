package org.around.ieuri.community.application.post.required

import org.around.ieuri.common.pagination.CursorPage
import org.around.ieuri.community.application.post.provided.dto.PostView
import org.around.ieuri.community.application.post.required.dto.PostsSearch

/** 거리순 피드 전용 읽기 포트. PostGIS 함수가 필요해 raw SQL 어댑터가 구현한다. */
interface PostSpatialQueryPort {
    /**
     * 반경 안의 글을 가까운 순으로 한 페이지. 같은 거리면 최신순이다.
     * - 모더레이션·카테고리·태그 필터를 이 조회가 함께 걸어, 요청 개수가 곧 페이지 크기다.
     */
    fun findPostsByDistance(search: PostsSearch): CursorPage<PostView>
}
