package org.around.ieuri.community.domain.photo

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 매직바이트 판정. 이미지를 파싱하지 않고 앞부분 몇 바이트만 본다. */
class ImageFormatTest {

    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    /** 시그니처 뒤를 0으로 채워 읽어 온 길이만큼 만든다. */
    private fun padded(prefix: ByteArray) = prefix + ByteArray(ImageFormat.MAGIC_BYTES_LENGTH - prefix.size)

    private val jpeg = bytes(0xFF, 0xD8, 0xFF)
    private val png = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    /** RIFF 컨테이너: "RIFF" + 크기 4바이트(무엇이든 된다) + "WEBP". */
    private val webp = bytes(
        0x52, 0x49, 0x46, 0x46,
        0x2A, 0x00, 0x00, 0x00,
        0x57, 0x45, 0x42, 0x50,
    )

    @Test
    fun `세 형식의 시그니처를 알아본다`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(padded(jpeg)))
        assertEquals(ImageFormat.PNG, ImageFormat.detect(padded(png)))
        assertEquals(ImageFormat.WEBP, ImageFormat.detect(webp))
    }

    @Test
    fun `webp는 크기 구간의 값과 무관하게 알아본다`() {
        val otherSize = webp.copyOf().also {
            it[4] = 0xFF.toByte()
            it[5] = 0xFF.toByte()
            it[6] = 0xFF.toByte()
            it[7] = 0xFF.toByte()
        }

        assertEquals(ImageFormat.WEBP, ImageFormat.detect(otherSize))
    }

    @Test
    fun `RIFF지만 WEBP가 아니면 거부한다`() {
        // WAV도 RIFF 컨테이너다 — 앞 네 바이트만 보면 통과해 버린다.
        val wav = webp.copyOf().also {
            it[8] = 0x57.toByte()
            it[9] = 0x41.toByte()
            it[10] = 0x56.toByte()
            it[11] = 0x45.toByte()
        }

        assertNull(ImageFormat.detect(wav))
    }

    @Test
    fun `확장자만 바꾼 파일은 걸러 낸다`() {
        // 실행 파일·PDF·빈 바이트 — 어느 것도 이미지 시그니처가 아니다.
        assertNull(ImageFormat.detect(padded(bytes(0x4D, 0x5A))))
        assertNull(ImageFormat.detect(padded(bytes(0x25, 0x50, 0x44, 0x46))))
        assertNull(ImageFormat.detect(ByteArray(ImageFormat.MAGIC_BYTES_LENGTH)))
    }

    @Test
    fun `시그니처보다 짧은 바이트열은 거부한다`() {
        // 빈 파일이나 잘린 업로드가 통과하면 안 된다.
        assertNull(ImageFormat.detect(ByteArray(0)))
        assertNull(ImageFormat.detect(bytes(0xFF, 0xD8)))
        // RIFF 네 바이트만으로는 WEBP인지 알 수 없다.
        assertNull(ImageFormat.detect(bytes(0x52, 0x49, 0x46, 0x46)))
    }

    @Test
    fun `허용 MIME 타입을 형식으로 바꾼다`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromContentType("image/jpeg"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromContentType("image/png"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromContentType("image/webp"))
    }

    @Test
    fun `MIME 타입의 대소문자와 공백을 가리지 않는다`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromContentType("  IMAGE/JPEG  "))
    }

    @Test
    fun `지원하지 않는 타입은 null이다`() {
        assertNull(ImageFormat.fromContentType("image/gif"))
        assertNull(ImageFormat.fromContentType("image/svg+xml"))
        assertNull(ImageFormat.fromContentType(""))
    }

    @Test
    fun `읽어야 하는 길이는 가장 긴 시그니처를 담는다`() {
        // 이 값보다 적게 읽으면 WEBP 판정이 실패한다.
        assertEquals(12, ImageFormat.MAGIC_BYTES_LENGTH)
        assertEquals(ImageFormat.MAGIC_BYTES_LENGTH, webp.size)
    }
}
