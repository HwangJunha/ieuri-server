package org.around.ieuri.community.application.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.community.application.command.ConfirmPostImageUploadsCommand
import org.around.ieuri.community.application.command.DeletePostImageCommand
import org.around.ieuri.community.application.command.IssuePostImageUploadUrlsCommand
import org.around.ieuri.community.application.command.PostImageUploadRequest
import org.around.ieuri.community.application.exception.PhotoNotUploadedException
import org.around.ieuri.community.application.exception.TooManyPhotosException
import org.around.ieuri.community.application.exception.UnsupportedContentTypeException
import org.around.ieuri.community.application.policy.CommunityPolicy
import org.around.ieuri.community.application.port.out.client.ClockPort
import org.around.ieuri.community.application.port.out.client.ObjectStoragePort
import org.around.ieuri.community.domain.vo.ImageFormat
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * 사진 업로드 발급·확인·삭제.
 *
 * 여기서 지키는 계약은 세 가지다. **발급은 all-or-nothing**(부분 발급은 클라이언트가
 * 추적하기 어렵다), **확인은 장별로 성패가 갈리고**(한 장 실패로 나머지를 버리지 않는다),
 * **키의 소유권은 저장소를 묻기 전에 문자열만으로 판정한다**(타인 키의 존재 여부가 응답
 * 시간으로 새어 나가지 않게 한다).
 */
class PostImageUploadServiceTest {

    private val memberId = 7L
    private val otherMemberId = 99L
    private val now = Instant.parse("2026-08-05T00:00:00Z")
    private val ttl = Duration.ofMinutes(30)
    private val maxBytes = 10_485_760L

    private val storage = mockk<ObjectStoragePort>()
    private val clock = mockk<ClockPort> { every { now() } returns now }
    private val policy = CommunityPolicy(
        maxPageSize = 50,
        commentPreviewSize = 3,
        maxRadiusMeters = 100_000,
        uploadUrlTtl = ttl,
        maxUploadBytes = maxBytes,
        allowedContentTypes = setOf("image/jpeg", "image/png", "image/webp"),
    )

    private val service = PostImageUploadService(storage, clock, policy)

    private fun jpeg(bytes: Long = 1_024L) = PostImageUploadRequest("image/jpeg", bytes)

    private fun pendingKey(owner: Long = memberId) = "pending/community/$owner/${UUID.randomUUID()}"

    /** 실제 JPEG 시그니처. 매직바이트 판정을 통과하는 최소 바이트열이다. */
    private fun jpegMagic() = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) +
        ByteArray(ImageFormat.MAGIC_BYTES_LENGTH - 3)

    private fun givenPresignReturnsUrl() {
        every { storage.presignPut(any(), any(), any(), any()) } answers {
            "https://storage.example/${firstArg<String>()}?sig=x"
        }
    }

    // ---------- 발급 ----------

    @Test
    fun `여러 장을 한 번에 발급하고 요청한 순서를 지킨다`() {
        givenPresignReturnsUrl()

        val urls = service.issueUploadUrls(
            IssuePostImageUploadUrlsCommand(
                listOf(jpeg(100), PostImageUploadRequest("image/png", 200)),
            ),
            memberId,
        )

        assertEquals(2, urls.size)
        // 순서가 뒤바뀌면 클라이언트가 어느 URL이 어느 파일인지 알 수 없다.
        verify { storage.presignPut(any(), "image/jpeg", 100, now.plus(ttl)) }
        verify { storage.presignPut(any(), "image/png", 200, now.plus(ttl)) }
    }

    @Test
    fun `발급된 키는 pending 접두어와 호출자 id를 갖는다`() {
        givenPresignReturnsUrl()

        val key = service.issueUploadUrls(IssuePostImageUploadUrlsCommand(listOf(jpeg())), memberId)
            .single()
            .objectKey

        // 키에 memberId가 들어가는 것이 나중에 소유권 판정의 근거가 된다.
        assertTrue(key.startsWith("pending/community/$memberId/"), "키가 pending/community/$memberId/로 시작하지 않는다: $key")
    }

    @Test
    fun `만료는 요청 전체가 같다`() {
        givenPresignReturnsUrl()

        val urls = service.issueUploadUrls(
            IssuePostImageUploadUrlsCommand(listOf(jpeg(), jpeg(), jpeg())),
            memberId,
        )

        // 장마다 다르면 어느 것이 먼저 죽는지 클라이언트가 알기 어렵다.
        assertEquals(setOf(now.plus(ttl)), urls.map { it.expiresAt }.toSet())
    }

    @Test
    fun `빈 목록은 오류가 아니라 빈 결과다`() {
        val urls = service.issueUploadUrls(IssuePostImageUploadUrlsCommand(emptyList()), memberId)

        assertTrue(urls.isEmpty())
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `상한을 넘는 장수는 거부한다`() {
        assertFailsWith<TooManyPhotosException> {
            service.issueUploadUrls(IssuePostImageUploadUrlsCommand(List(6) { jpeg() }), memberId)
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `한 장이라도 형식이 어긋나면 전부 발급하지 않는다`() {
        // 부분 발급은 클라이언트가 어느 장이 실패했는지 추적해야 해서 복잡도가 이득보다 크다.
        assertFailsWith<UnsupportedContentTypeException> {
            service.issueUploadUrls(
                IssuePostImageUploadUrlsCommand(
                    listOf(jpeg(), PostImageUploadRequest("image/gif", 100), jpeg()),
                ),
                memberId,
            )
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `한 장이라도 크기가 범위를 벗어나면 전부 발급하지 않는다`() {
        assertFailsWith<UnsupportedContentTypeException> {
            service.issueUploadUrls(
                IssuePostImageUploadUrlsCommand(listOf(jpeg(), jpeg(maxBytes + 1))),
                memberId,
            )
        }
        verify(exactly = 0) { storage.presignPut(any(), any(), any(), any()) }
    }

    @Test
    fun `크기 0 이하는 거부한다`() {
        assertFailsWith<UnsupportedContentTypeException> {
            service.issueUploadUrls(IssuePostImageUploadUrlsCommand(listOf(jpeg(0))), memberId)
        }
    }

    @Test
    fun `상한과 정확히 같은 크기는 허용한다`() {
        givenPresignReturnsUrl()

        // 경계값이 어느 쪽에 속하는지가 클라이언트의 사전 검사와 어긋나면 안 된다.
        val urls = service.issueUploadUrls(
            IssuePostImageUploadUrlsCommand(listOf(jpeg(maxBytes))),
            memberId,
        )

        assertEquals(1, urls.size)
    }

    @Test
    fun `타입의 대소문자와 공백은 정규화해 서명한다`() {
        givenPresignReturnsUrl()

        service.issueUploadUrls(
            IssuePostImageUploadUrlsCommand(listOf(PostImageUploadRequest("  IMAGE/JPEG  ", 100))),
            memberId,
        )

        // 서명에 들어가는 값이 정규화된 형태여야 업로드 시 Content-Type과 맞출 수 있다.
        verify { storage.presignPut(any(), "image/jpeg", 100, any()) }
    }

    // ---------- 확인 ----------

    @Test
    fun `확인을 통과하면 images 키로 옮기고 확정 키를 돌려준다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, ImageFormat.MAGIC_BYTES_LENGTH) } returns jpegMagic()
        every { storage.move(any(), any()) } returns Unit

        val result = service.confirmUploads(ConfirmPostImageUploadsCommand(listOf(key)), memberId)

        val expected = key.replaceFirst("pending/", "images/")
        assertContentEquals(listOf(expected), result.confirmed)
        assertTrue(result.failed.isEmpty())
        verify { storage.move(key, expected) }
    }

    @Test
    fun `한 장이 실패해도 나머지는 확정된다`() {
        val good = pendingKey()
        val bad = pendingKey()
        every { storage.findSize(good) } returns 1_024
        every { storage.readPrefix(good, any()) } returns jpegMagic()
        every { storage.move(any(), any()) } returns Unit
        // 업로드되지 않은 키다.
        every { storage.findSize(bad) } returns null

        val result = service.confirmUploads(
            ConfirmPostImageUploadsCommand(listOf(good, bad)),
            memberId,
        )

        // 전부 버리면 사용자가 성공한 장까지 다시 올려야 한다.
        assertContentEquals(listOf(good.replaceFirst("pending/", "images/")), result.confirmed)
        assertEquals(listOf(bad), result.failed.map { it.objectKey })
        verify(exactly = 0) { storage.move(bad, any()) }
    }

    @Test
    fun `매직바이트가 형식과 맞지 않으면 실패한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns 1_024
        // 확장자만 바꾼 파일 — 선언은 이미지지만 내용이 아니다.
        every { storage.readPrefix(key, any()) } returns ByteArray(ImageFormat.MAGIC_BYTES_LENGTH)

        val result = service.confirmUploads(ConfirmPostImageUploadsCommand(listOf(key)), memberId)

        assertTrue(result.confirmed.isEmpty())
        assertEquals(listOf(key), result.failed.map { it.objectKey })
        verify(exactly = 0) { storage.move(any(), any()) }
    }

    @Test
    fun `선언보다 크게 올린 파일은 확인 단계에서 걸린다`() {
        val key = pendingKey()
        // 서명을 우회한 경로 대비 — 실제 크기를 다시 본다.
        every { storage.findSize(key) } returns maxBytes + 1

        val result = service.confirmUploads(ConfirmPostImageUploadsCommand(listOf(key)), memberId)

        assertEquals(listOf(key), result.failed.map { it.objectKey })
        verify(exactly = 0) { storage.readPrefix(any(), any()) }
    }

    @Test
    fun `남의 키는 저장소를 묻지 않고 거부한다`() {
        val key = pendingKey(otherMemberId)

        val result = service.confirmUploads(ConfirmPostImageUploadsCommand(listOf(key)), memberId)

        assertEquals(listOf(key), result.failed.map { it.objectKey })
        // 저장소를 먼저 물으면 타인의 키가 존재하는지 응답 시간으로 알아낼 수 있다.
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `형식이 깨진 키는 저장소를 묻지 않고 거부한다`() {
        val result = service.confirmUploads(
            ConfirmPostImageUploadsCommand(listOf("pending/community/7/uuid가-아님", "말도-안-되는-키")),
            memberId,
        )

        assertEquals(2, result.failed.size)
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `이미 확정된 키를 다시 확인하면 실패한다`() {
        // 원본이 pending에 없으므로 재확인은 성공할 수 없다. 조용히 통과시키면
        // 클라이언트가 move가 두 번 일어났다고 착각한다.
        val key = "images/community/$memberId/${UUID.randomUUID()}"

        val result = service.confirmUploads(ConfirmPostImageUploadsCommand(listOf(key)), memberId)

        assertEquals(listOf(key), result.failed.map { it.objectKey })
        verify(exactly = 0) { storage.findSize(any()) }
    }

    @Test
    fun `같은 키를 여러 번 넘겨도 한 번만 처리한다`() {
        val key = pendingKey()
        every { storage.findSize(key) } returns 1_024
        every { storage.readPrefix(key, any()) } returns jpegMagic()
        every { storage.move(any(), any()) } returns Unit

        val result = service.confirmUploads(
            ConfirmPostImageUploadsCommand(listOf(key, key, key)),
            memberId,
        )

        assertEquals(1, result.confirmed.size)
        verify(exactly = 1) { storage.move(key, any()) }
    }

    @Test
    fun `실패 사유는 원인을 구분하지 않는다`() {
        // 미업로드·타인 키·위조 키를 구분해 주면 키 존재 여부를 캐낼 수 있다.
        val notUploaded = pendingKey()
        val someoneElses = pendingKey(otherMemberId)
        every { storage.findSize(notUploaded) } returns null

        val result = service.confirmUploads(
            ConfirmPostImageUploadsCommand(listOf(notUploaded, someoneElses, "깨진키")),
            memberId,
        )

        assertEquals(setOf("PHOTO_NOT_UPLOADED"), result.failed.map { it.code }.toSet())
    }

    // ---------- 삭제 ----------

    @Test
    fun `본인 키는 임시든 확정이든 지운다`() {
        val pending = pendingKey()
        val confirmed = "images/community/$memberId/${UUID.randomUUID()}"
        every { storage.delete(any()) } returns Unit

        service.deleteImage(DeletePostImageCommand(pending), memberId)
        service.deleteImage(DeletePostImageCommand(confirmed), memberId)

        verify { storage.delete(pending) }
        verify { storage.delete(confirmed) }
    }

    @Test
    fun `남의 키는 지우지 않는다`() {
        assertFailsWith<PhotoNotUploadedException> {
            service.deleteImage(DeletePostImageCommand(pendingKey(otherMemberId)), memberId)
        }
        verify(exactly = 0) { storage.delete(any()) }
    }

    @Test
    fun `형식이 깨진 키는 지우지 않는다`() {
        assertFailsWith<PhotoNotUploadedException> {
            service.deleteImage(DeletePostImageCommand("../../etc/passwd"), memberId)
        }
        verify(exactly = 0) { storage.delete(any()) }
    }
}
