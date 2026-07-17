package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.around.ieuri.auth.application.port.out.client.SnsTokenRevokerPort
import org.around.ieuri.auth.domain.vo.Provider
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * 개발/임시용 SNS 토큰 폐기기. 실제 revoke는 provider 자격증명 + 저장된 토큰이 필요하다
 * (현재 SNS 토큰을 저장하지 않으므로 실제 호출 불가). Apple 계정 삭제 요구사항에 대비해 seam만 둔다.
 *
 * TODO(prod): Apple은 로그인 시 authorization code로 refresh token을 발급받아 저장해 두었다가
 *   탈퇴 시 https://appleid.apple.com/auth/revoke 로 폐기한다. (client_secret JWT 필요)
 */
@Component
class DevSnsTokenRevoker : SnsTokenRevokerPort {

    private val log = LoggerFactory.getLogger(DevSnsTokenRevoker::class.java)

    override fun revoke(provider: Provider, providerUserId: String) {
        if (provider == Provider.APPLE) {
            log.info("[dev] Apple 토큰 revoke 대상(providerUserId={}). 실제 폐기는 자격증명 준비 후 구현.", providerUserId)
        }
    }
}
