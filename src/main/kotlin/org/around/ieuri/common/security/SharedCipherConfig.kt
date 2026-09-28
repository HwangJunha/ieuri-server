package org.around.ieuri.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 개인정보 at-rest 암호화 빈(공유 커널).
 *
 * **여러 도메인이 같은 키를 쓴다** — auth의 이메일과 community의 DM 본문이다(SPEC-0017 R16).
 * 도메인마다 키를 나누면 배포 환경마다 주입할 비밀값이 늘어나는데, 두 값의 유출 반경이
 * 실질적으로 같아(같은 애플리케이션·같은 DB) 나눠 얻는 것이 없다.
 *
 * 키는 환경변수로만 주입되며 기본값이 없다 — 없으면 부팅이 실패한다(fail-fast).
 */
@Configuration
class SharedCipherConfig {

    @Bean
    fun aesGcmCipher(@Value("\${ieuri.encryption.key}") key: String): AesGcmCipher = AesGcmCipher(key)
}
