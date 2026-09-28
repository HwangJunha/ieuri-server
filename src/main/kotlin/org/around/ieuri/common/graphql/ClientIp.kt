package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment

/**
 * 요청의 클라이언트 IP를 실행 컨텍스트(DgsContext)에서 읽는다 — ThreadLocal을 쓰지 않는다.
 * - `CF-Connecting-IP`를 먼저, 다음은 `X-Forwarded-For`의 맨 앞 값이다.
 * - 원격 주소로 떨어지지 않는다(클러스터 내부 주소라 무의미). 알 수 없으면 null이다.
 */
fun DataFetchingEnvironment.clientIp(): String? {
    // 어떤 값이 와도 던지지 않는다 — 판정이 예외를 내면 로그인·탈퇴 자체가 실패한다.
    // DGS 컨텍스트가 없는 경로에서는 플랫폼 타입 null이라 `?.`만으로는 NPE가 난다.
    val headers = try {
        DgsContext.getRequestData(this)?.headers
    } catch (e: RuntimeException) {
        null
    } ?: return null

    headers.getFirst(CF_CONNECTING_IP)?.trimmedOrNull()?.let { return it.take(IP_MAX_LENGTH) }
    return headers.getFirst(X_FORWARDED_FOR)
        ?.substringBefore(',')
        ?.trimmedOrNull()
        ?.take(IP_MAX_LENGTH)
}

private fun String.trimmedOrNull(): String? = trim().ifEmpty { null }

private const val CF_CONNECTING_IP = "CF-Connecting-IP"
private const val X_FORWARDED_FOR = "X-Forwarded-For"

/** 컬럼 길이(varchar(45))와 같다 — IPv4-mapped IPv6의 최대 길이다. */
private const val IP_MAX_LENGTH = 45
