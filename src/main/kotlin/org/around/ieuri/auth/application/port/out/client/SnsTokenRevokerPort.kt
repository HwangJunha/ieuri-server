package org.around.ieuri.auth.application.port.out.client

import org.around.ieuri.auth.domain.vo.Provider

/**
 * 탈퇴 시 SNS provider 측 토큰을 폐기하는 포트(주로 Apple 요구사항).
 * 실제 revoke는 provider 자격증명·저장된 토큰이 필요하므로, 지금은 dev 구현으로 추상화만 둔다.
 */
interface SnsTokenRevokerPort {
    fun revoke(provider: Provider, providerUserId: String)
}
