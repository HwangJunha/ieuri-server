package org.around.ieuri.auth.domain.token

import java.time.Instant

/** WebSocket 연결의 신원. [hasActiveToken]=false는 리프레시 토큰이 전부 폐기됐다는 뜻이다(탈퇴·정지·재사용 감지). */
data class SocketIdentity(
    val connectedAt: Instant,
    val hasActiveToken: Boolean,
)
