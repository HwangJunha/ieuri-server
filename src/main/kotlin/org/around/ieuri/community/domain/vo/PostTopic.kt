package org.around.ieuri.community.domain.vo

/**
 * 글의 주제 태그. 고정 세트이며 글당 정확히 하나다.
 * 표시 문자열은 서버가 내려주지 않는다 — 클라이언트가 언어별로 매핑한다.
 */
enum class PostTopic {
    QUESTION,
    TIP,
    REVIEW,
    COMPANION,
    PHOTO,
}
