package org.around.ieuri.community.domain.photo

import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AttachedPhotosTest {

    private fun confirmed(memberId: Long) = PhotoObjectKey.issue(memberId, UUID.randomUUID()).toConfirmed()

    @Test
    fun `작성자 본인의 확정 키는 순서를 지켜 5장까지 붙는다`() {
        val keys = List(AttachedPhotos.MAX_PHOTOS) { confirmed(7) }

        assertEquals(keys, AttachedPhotos.of(keys, authorId = 7).keys)
    }

    @Test
    fun `6장부터는 TOO_MANY_PHOTOS다`() {
        assertFailsWith<TooManyPhotosException> { AttachedPhotos.of(List(AttachedPhotos.MAX_PHOTOS + 1) { confirmed(7) }, 7) }
    }

    @Test
    fun `남의 키나 확정되지 않은 키는 PHOTO_NOT_UPLOADED다`() {
        assertFailsWith<PhotoNotUploadedException> { AttachedPhotos.of(listOf(confirmed(8)), authorId = 7) }
        assertFailsWith<PhotoNotUploadedException> { AttachedPhotos.of(listOf(PhotoObjectKey.issue(7, UUID.randomUUID())), authorId = 7) }
    }
}
