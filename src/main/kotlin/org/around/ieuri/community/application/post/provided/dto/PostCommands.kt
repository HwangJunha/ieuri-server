package org.around.ieuri.community.application.post.provided.dto

import org.around.ieuri.community.domain.shared.TargetCategory

// 글 쓰기 입력. 형식 검증만 하고 업무 규칙은 도메인이 판정한다. memberId는 담지 않는다.

data class CreatePostCommand(
    /** 글의 카테고리. 필수 — 상단 칩이 이 값으로 거른다. */
    val category: TargetCategory,
    /** 연결할 장소(카탈로그 id). null이면 카테고리에만 속하는 글이다. */
    val catalogId: Long?,
    /** 사용자가 입력한 태그. 최대 5개이며 중복은 하나로 합쳐진다. */
    val tags: List<String>,
    val title: String,
    val body: String,
    /** 확인 단계를 통과한 오브젝트 키. 배열 순서가 표시 순서다. */
    val photoKeys: List<String>,
)

/**
 * 글 수정. null은 "건드리지 않는다"이고 빈 값과 다르다(photoKeys가 빈 배열이면 전부 뗀다).
 * - [catalogId]만 예외로, [changeSpot] 플래그로 의도를 받는다.
 */
data class EditPostCommand(
    val postId: Long,
    val title: String,
    val body: String,
    val photoKeys: List<String>?,
    val category: TargetCategory? = null,
    /** true면 [catalogId]로 장소를 바꾸고 catalogId가 null이면 뗀다. false면 장소를 건드리지 않는다. */
    val changeSpot: Boolean = false,
    val catalogId: Long? = null,
    val tags: List<String>? = null,
) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
        require(catalogId == null || catalogId > 0) { "catalogId는 양수여야 합니다." }
        require(changeSpot || catalogId == null) { "장소를 바꾸지 않으면서 catalogId를 줄 수 없습니다." }
    }
}

/** 삭제·좋아요·숨김처럼 글 하나를 가리키는 입력. */
data class PostTargetCommand(val postId: Long) {
    init {
        require(postId > 0) { "postId는 양수여야 합니다." }
    }
}
