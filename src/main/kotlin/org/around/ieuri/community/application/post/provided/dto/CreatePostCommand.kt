package org.around.ieuri.community.application.post.provided.dto

import org.around.ieuri.community.domain.post.TargetCategory

/** 글 쓰기 입력. 형식 검증만 하고 업무 규칙은 도메인이 판정한다. memberId는 담지 않는다. */
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
