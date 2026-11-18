package org.around.ieuri.community.application.post.provided.dto

import org.around.ieuri.community.domain.post.TargetCategory

/**
 * 글 수정. null은 "건드리지 않는다"이고 빈 값과 다르다(photoKeys가 빈 배열이면 전부 뗀다).
 * - [catalogId]만 예외로, [changeSpot] 플래그로 의도를 받는다.
 * - 형식 검증만 하고 업무 규칙은 도메인이 판정한다. memberId는 담지 않는다.
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
