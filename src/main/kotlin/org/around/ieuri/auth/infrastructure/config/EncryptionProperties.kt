package org.around.ieuri.auth.infrastructure.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 개인정보 암호화 비밀값. prod는 환경변수로 주입(기본값 없이 → 없으면 부팅 실패, fail-fast),
 * local은 운영과 다른 개발 전용 값을 사용한다.
 * - key: AES-256용 base64 인코딩 32바이트 키
 * - pepper: 검증 코드 해시에 섞는 pepper
 * - identifierPepper: 로그인 식별자 보호(HMAC)용 pepper. 검증 코드용과 **분리한다** —
 *   이 값이 바뀌면 기존 EMAIL 회원이 로그인 불가가 되어 사실상 교체할 수 없는데,
 *   한 값을 공유하면 교체 가능해야 할 검증 코드 pepper까지 묶여버린다.
 */
@ConfigurationProperties(prefix = "ieuri.encryption")
data class EncryptionProperties(
    val key: String,
    val pepper: String,
    val identifierPepper: String,
)
