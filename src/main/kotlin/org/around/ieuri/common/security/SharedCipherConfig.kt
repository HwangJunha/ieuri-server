package org.around.ieuri.common.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 개인정보 at-rest 암호화 빈. auth의 이메일과 community의 DM 본문이 같은 키를 쓴다(SPEC-0017).
 * 키는 환경변수로만 주입되며 없으면 부팅이 실패한다.
 */
@Configuration
class SharedCipherConfig {

    @Bean
    fun aesGcmCipher(@Value("\${ieuri.encryption.key}") key: String): AesGcmCipher = AesGcmCipher(key)
}
