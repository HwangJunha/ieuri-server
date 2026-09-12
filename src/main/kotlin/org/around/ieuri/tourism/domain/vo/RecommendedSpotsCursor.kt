package org.around.ieuri.tourism.domain.vo

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.Locale

/**
 * 추천 목록의 조건(SPEC-0035 R4). 좌표(소수 5자리 반올림)·반경·정렬된 카테고리·언어·호출자를
 * 해시 하나로 접는다. 같은 조건이면 같은 해시이고, 커서와 캐시 키가 이 해시에 묶인다.
 *
 * 호출자를 포함하는 이유: 로그인 회원은 반응 제외로 후보 집합이 달라, 해시가 같으면 다른
 * 회원의 묶음을 캐시에서 받게 된다. toString은 좌표를 노출하지 않는다.
 */
data class RecommendedSpotsConditions(
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
            .joinToString("") { "%02x".format(it) }
    }

    override fun toString(): String = "RecommendedSpotsConditions(hash=$hash)"
}

/**
 * 추천 목록 커서(SPEC-0035 R4). `r1|{조건 해시}|{위치}|{발급 epoch초}`를 base64url로 담는다.
 * 서버 상태가 없어 커서만으로 같은 순열을 재계산할 수 있다. 서명은 두지 않는다 — 서버는 커서 값을
 * 신뢰하지 않고 조건을 요청에서 다시 계산해 비교하므로 위조로 얻을 수 있는 것이 없다.
 *
 * [position]은 이 커서 **다음** 항목의 순서상 위치(0-base)다. [issuedAt]은 세션이 시작된 시각이며
 * 이어 받아도 갱신하지 않는다 — 수명은 세션 전체에 걸린다.
 */
data class RecommendedSpotsCursor(
    val conditionsHash: String,
    val position: Int,
    val issuedAt: Instant,
) {
    init {
        require(position >= 0) { "커서 위치는 음수일 수 없습니다." }
    }

    fun encode(): String =
        Base64.getUrlEncoder().withoutPadding()
            .encodeToString("$PREFIX|$conditionsHash|$position|${issuedAt.epochSecond}".toByteArray(StandardCharsets.UTF_8))

    fun isExpired(now: Instant, maxAge: Duration): Boolean = issuedAt.plus(maxAge).isBefore(now)

    companion object {
        private const val PREFIX = "r1"

        /** 형식이 깨진 커서는 [IllegalArgumentException]이다 — 조용히 첫 페이지로 되돌리지 않는다. */
        fun decode(cursor: String): RecommendedSpotsCursor =
            try {
                val parts = String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("|")
                require(parts.size == 4 && parts[0] == PREFIX) { "추천 목록 커서가 아닙니다." }
                RecommendedSpotsCursor(
                    conditionsHash = parts[1],
                    position = parts[2].toInt(),
                    issuedAt = Instant.ofEpochSecond(parts[3].toLong()),
                )
            } catch (e: RuntimeException) {
                throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
            }
    }
}
