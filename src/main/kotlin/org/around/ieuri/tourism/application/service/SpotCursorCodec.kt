package org.around.ieuri.tourism.application.service

import org.around.ieuri.tourism.domain.aggregate.Spot
import java.util.Base64

/**
 * spots 커서 코덱. 커서 = (거리, contentId) 복합 정렬키 자체를 불투명 토큰으로 인코딩한다(AC-1).
 * upstream(arrange=Q)은 거리순이 아니므로 커서 안정성은 upstream pageNo가 아니라 이 키에 귀속된다.
 *
 * 형식: base64url( "<distanceMeters>|<contentId>" ). distanceMeters는 소수점 표현이 안정적이도록 문자열화.
 * contentId에 '|'가 들어올 가능성은 없으나(TourAPI 숫자 id), 방어적으로 첫 '|'만 구분자로 사용한다.
 */
object SpotCursorCodec {
    private val encoder = Base64.getUrlEncoder().withoutPadding()
    private val decoder = Base64.getUrlDecoder()

    fun encode(key: Spot.SortKey): String {
        val raw = "${key.distanceMeters}|${key.id}"
        return encoder.encodeToString(raw.toByteArray(Charsets.UTF_8))
    }

    /** 잘못된 커서는 IllegalArgumentException(인바운드에서 INVALID 처리). */
    fun decode(cursor: String): Spot.SortKey {
        val raw = try {
            String(decoder.decode(cursor), Charsets.UTF_8)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("유효하지 않은 커서입니다.", e)
        }
        val sep = raw.indexOf('|')
        require(sep > 0) { "유효하지 않은 커서입니다." }
        val distance = raw.substring(0, sep).toDoubleOrNull()
            ?: throw IllegalArgumentException("유효하지 않은 커서입니다.")
        val id = raw.substring(sep + 1)
        require(id.isNotBlank()) { "유효하지 않은 커서입니다." }
        return Spot.SortKey(distance, id)
    }
}
