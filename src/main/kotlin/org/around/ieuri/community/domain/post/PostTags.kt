package org.around.ieuri.community.domain.post

/**
 * 글의 태그 목록. 최대 [PostTag.MAX_PER_POST]개이며 정규화가 같은 태그는 하나로 본다.
 * - 생성자와 `copy`를 막아 [of]의 검증을 거쳐야만 만들어진다.
 */
@ConsistentCopyVisibility
data class PostTags private constructor(val values: List<PostTag>) {
    companion object {
        fun of(tags: List<PostTag>): PostTags {
            if (tags.size > PostTag.MAX_PER_POST) throw TooManyTagsException()
            return PostTags(tags.distinctBy { it.normalized })
        }
    }
}
