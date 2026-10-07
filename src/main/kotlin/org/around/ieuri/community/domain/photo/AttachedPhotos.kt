package org.around.ieuri.community.domain.photo

/**
 * 글·질문·답변·답글에 붙는 사진 목록(표시 순서). 본인에게 발급되고 업로드 확인(`images/`)을 마친 키만 붙일 수 있다.
 * - 생성자와 `copy`를 막아 [of]의 검증을 거쳐야만 만들어진다.
 */
@ConsistentCopyVisibility
data class AttachedPhotos private constructor(val keys: List<PhotoObjectKey>) {
    companion object {
        const val MAX_PHOTOS = 5

        /** 실제 오브젝트 존재는 애플리케이션이 확인한다. */
        fun of(keys: List<PhotoObjectKey>, authorId: Long): AttachedPhotos {
            if (keys.size > MAX_PHOTOS) throw TooManyPhotosException()
            if (!keys.all { it.ownedBy(authorId) && it.confirmed }) throw PhotoNotUploadedException()
            return AttachedPhotos(keys)
        }
    }
}
