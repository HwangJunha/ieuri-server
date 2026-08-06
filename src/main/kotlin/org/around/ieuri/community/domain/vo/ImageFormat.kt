package org.around.ieuri.community.domain.vo

/**
 * 지원 이미지 형식과 그 매직바이트.
 *
 * **이미지를 파싱하지 않는다** — 앞부분 몇 바이트가 형식 시그니처와 맞는지만 본다.
 * 파서를 태우면 이미지 파서 취약점(ImageTragick·libwebp CVE 계열)에 노출되고, 서버 부하도
 * 늘어난다. 리사이즈·크롭은 CDN의 이미지 변환이 처리한다.
 *
 * 이 검사는 위조를 완전히 막지 못한다 — 시그니처만 맞춘 파일은 통과한다. 그래서 검증을
 * 여러 층에 나눠 건다: 서명에 포함된 Content-Type·Content-Length, 확인 단계의 실제 크기,
 * 그리고 여기의 매직바이트다. 어느 한 층도 단독으로는 완전하지 않다.
 */
enum class ImageFormat(val contentType: String) {
    JPEG("image/jpeg") {
        override fun matches(prefix: ByteArray): Boolean =
            prefix.startsWith(0xFF, 0xD8, 0xFF)
    },
    PNG("image/png") {
        override fun matches(prefix: ByteArray): Boolean =
            prefix.startsWith(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    },
    WEBP("image/webp") {
        /** RIFF 컨테이너: "RIFF" + 4바이트 크기 + "WEBP". 크기 구간은 값이 달라 건너뛴다. */
        override fun matches(prefix: ByteArray): Boolean =
            prefix.size >= 12 &&
                prefix.startsWith(0x52, 0x49, 0x46, 0x46) &&
                prefix[8] == 0x57.toByte() && prefix[9] == 0x45.toByte() &&
                prefix[10] == 0x42.toByte() && prefix[11] == 0x50.toByte()
    },
    ;

    abstract fun matches(prefix: ByteArray): Boolean

    companion object {
        /** 매직바이트 판정에 필요한 최대 바이트 수(WebP의 RIFF 헤더가 가장 길다). */
        const val MAGIC_BYTES_LENGTH = 12

        fun fromContentType(contentType: String): ImageFormat? =
            entries.firstOrNull { it.contentType == contentType.trim().lowercase() }

        /** 앞부분 바이트가 지원 형식 중 하나와 맞는지. 어느 것도 아니면 null. */
        fun detect(prefix: ByteArray): ImageFormat? = entries.firstOrNull { it.matches(prefix) }
    }
}

private fun ByteArray.startsWith(vararg signature: Int): Boolean {
    if (size < signature.size) return false
    return signature.withIndex().all { (index, expected) -> this[index] == expected.toByte() }
}
