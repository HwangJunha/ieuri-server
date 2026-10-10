package org.around.ieuri.auth.adapter.config
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 개인정보 암호화 비밀값. prod는 환경변수 주입(기본값 없음 → 부팅 실패), local은 개발 전용 값.
 * - key: AES-256용 base64 32바이트 키
 * - pepper: 검증 코드 해시용 pepper
 * - identifierPepper: 로그인 식별자 HMAC용. 바뀌면 기존 EMAIL 회원이 로그인 불가라 교체할 수 없다 — pepper와 공유하지 않는다.
 */
@ConfigurationProperties(prefix = "ieuri.encryption")
data class EncryptionProperties(
    val key: String,
    val pepper: String,
    val identifierPepper: String,
)
