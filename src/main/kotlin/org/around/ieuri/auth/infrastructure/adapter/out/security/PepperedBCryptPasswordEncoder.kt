package org.around.ieuri.auth.infrastructure.adapter.out.security

import org.around.ieuri.auth.application.port.out.security.PasswordEncoderPort
import org.around.ieuri.auth.infrastructure.config.PasswordProperties
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 비밀번호 단방향 해시 구현: `bcrypt( HMAC-SHA256(rawPassword, pepper) )`.
 * - BCrypt: 솔트 내장의 표준 단방향 해시(work factor로 계산 비용 조절).
 * - pepper: 서버 비밀키. DB에 없으므로 DB 유출만으로는 크래킹이 어렵다(OWASP 권고).
 * HMAC로 선처리하므로 BCrypt의 72바이트 입력 제한 문제도 자연히 해소된다.
 */
@Component
class PepperedBCryptPasswordEncoder(
    properties: PasswordProperties,
) : PasswordEncoderPort {

    private val bcrypt = BCryptPasswordEncoder()
    private val pepperKey: SecretKeySpec

    init {
        require(properties.pepper.isNotBlank()) { "비밀번호 pepper가 설정되지 않았습니다." }
        pepperKey = SecretKeySpec(properties.pepper.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
    }

    override fun encode(rawPassword: String): String = checkNotNull(bcrypt.encode(peppered(rawPassword))) { "비밀번호 해시 생성에 실패했습니다." }

    override fun matches(rawPassword: String, encodedPassword: String): Boolean = bcrypt.matches(peppered(rawPassword), encodedPassword)

    private fun peppered(rawPassword: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM).apply { init(pepperKey) }
        return Base64.getEncoder().encodeToString(mac.doFinal(rawPassword.toByteArray(Charsets.UTF_8)))
    }

    private companion object {
        const val HMAC_ALGORITHM = "HmacSHA256"
    }
}
