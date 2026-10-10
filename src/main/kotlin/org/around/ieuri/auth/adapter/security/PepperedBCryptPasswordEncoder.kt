package org.around.ieuri.auth.adapter.security
import org.around.ieuri.auth.adapter.config.PasswordProperties
import org.around.ieuri.auth.application.member.required.PasswordEncoderPort
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 비밀번호 단방향 해시: `bcrypt( HMAC-SHA256(rawPassword, pepper) )`.
 * - pepper는 DB 밖의 서버 비밀키다. HMAC 선처리로 BCrypt의 72바이트 입력 제한도 피한다.
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
