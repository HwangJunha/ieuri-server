package org.around.ieuri.tourism.domain.vo

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 추천 피드 커서의 서명 키(SPEC-0035 R4). 환경변수에서 온다. toString은 값을 노출하지 않는다.
 */
class FeedCursorSecret(value: String) {
    init {
        require(value.isNotBlank()) { "피드 커서 서명 키가 비어 있습니다." }
    }

    internal val key = SecretKeySpec(value.toByteArray(StandardCharsets.UTF_8), FeedCursor.HMAC_ALGORITHM)

    override fun toString(): String = "FeedCursorSecret(masked)"
}

/**
 * 추천 피드의 조건(SPEC-0035 R4). 좌표(소수 5자리 반올림)·반경·정렬된 카테고리·언어·호출자를
 * 해시 하나로 접는다. 같은 조건이면 같은 해시이고, 커서와 캐시 키가 이 해시에 묶인다.
 *
 * 호출자를 포함하는 이유: 로그인 회원은 반응 제외로 후보 집합이 달라, 해시가 같으면 다른
 * 회원의 묶음을 캐시에서 받게 된다. toString은 좌표를 노출하지 않는다.
 */
data class FeedConditions(
    val center: Coordinates,
    val radiusMeters: Int,
    val categories: CategorySelection,
    val language: DomainLocale,
    val memberId: Long?,
) {
    val hash: String by lazy {
        val material = listOf(
            "%.5f".format(Locale.ROOT, center.latitude),
            "%.5f".format(Locale.ROOT, center.longitude),
            radiusMeters.toString(),
            categories.codes().joinToString(","),
            language.name,
            memberId?.toString() ?: "-",
        ).joinToString("|")
        MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(StandardCharsets.UTF_8))
            .toHex()
    }

    override fun toString(): String = "FeedConditions(hash=$hash)"
}

/**
 * 추천 피드 커서(SPEC-0035 R4). `f1|{조건 해시}|{위치}|{발급 epoch초}|{HMAC-SHA256}`을 base64url로 담는다.
 * 서버 상태가 없어 커서만으로 같은 순열을 재계산할 수 있고, 서명이 있어 조건 해시를 위조할 수 없다.
 *
 * [position]은 이 커서 **다음** 항목의 순서상 위치(0-base)다. [issuedAt]은 세션이 시작된 시각이며
 * 이어 받아도 갱신하지 않는다 — 수명은 세션 전체에 걸린다.
 */
data class FeedCursor(
    val conditionsHash: String,
    val position: Int,
    val issuedAt: Instant,
) {
    init {
        require(position >= 0) { "커서 위치는 음수일 수 없습니다." }
    }

    fun encode(secret: FeedCursorSecret): String {
        val payload = "$VERSION|$conditionsHash|$position|${issuedAt.epochSecond}"
        val signed = "$payload|${sign(payload, secret)}"
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signed.toByteArray(StandardCharsets.UTF_8))
    }

    fun isExpired(now: Instant, maxAge: Duration): Boolean = issuedAt.plus(maxAge).isBefore(now)

    companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
        private const val VERSION = "f1"

        /** 형식·서명 실패는 모두 [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리지 않는다. */
        fun decode(cursor: String, secret: FeedCursorSecret): FeedCursor =
            try {
                val decoded = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8)
                val parts = decoded.split("|")
                require(parts.size == 5 && parts[0] == VERSION) { "추천 피드 커서가 아닙니다." }
                val payload = parts.subList(0, 4).joinToString("|")
                require(MessageDigest.isEqual(sign(payload, secret).toByteArray(), parts[4].toByteArray())) {
                    "커서 서명이 맞지 않습니다."
                }
                FeedCursor(
                    conditionsHash = parts[1],
                    position = parts[2].toInt(),
                    issuedAt = Instant.ofEpochSecond(parts[3].toLong()),
                )
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
            }

        private fun sign(payload: String, secret: FeedCursorSecret): String =
            Mac.getInstance(HMAC_ALGORITHM)
                .apply { init(secret.key) }
                .doFinal(payload.toByteArray(StandardCharsets.UTF_8))
                .toHex()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
