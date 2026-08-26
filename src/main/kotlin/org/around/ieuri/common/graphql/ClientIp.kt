package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment

/**
 * 요청의 접속지 정보(클라이언트 IP)를 **실행 컨텍스트에서** 읽는다(공유 커널).
 *
 * [acceptLanguage]와 같은 방식이다 — `RequestContextHolder`(ThreadLocal)를 쓰지 않는다.
 * 가상 스레드에서 데이터페처가 다른 스레드로 옮겨가면 ThreadLocal은 사라지지만, DGS가
 * 서블릿 스레드에서 캡처해 `DgsContext`에 실어 둔 헤더는 그대로 있다.
 *
 * **`CF-Connecting-IP`를 먼저 본다.** 모든 요청이 Cloudflare Tunnel로만 들어오므로(공개
 * LoadBalancer가 없다) 이 헤더는 Cloudflare 엣지가 매 요청 덮어써 주는 값이고, 클라이언트가
 * 위조해 보내도 엣지에서 교체된다. `X-Forwarded-For`는 그 뒤에 보며, 쉼표로 이어진 목록의
 * **맨 앞**이 최초 클라이언트다.
 *
 * **원격 주소로 떨어지지 않는다.** 그 값은 cloudflared 파드나 Traefik의 클러스터 내부 주소라
 * 접속지 정보가 아니다 — 기록해 두면 전부 같은 사설 IP로 남아 기준을 충족하는 것처럼 보이면서
 * 실제로는 아무 것도 식별하지 못한다. 알 수 없으면 **null이다.**
 */
fun DataFetchingEnvironment.clientIp(): String? {
    // **어떤 값이 와도 던지지 않는다.** 접속기록은 부수 효과라 판정이 예외를 내면 로그인·탈퇴
    // 자체가 실패한다 — 기록을 남기려다 기록할 일을 못 하게 만드는 것이 최악이다.
    // DGS 컨텍스트가 없는 실행 경로(구독·테스트 등)에서 getRequestData가 null을 돌려주는데,
    // 자바 상호운용이라 플랫폼 타입이고 `?.`만으로는 NPE가 새어 나온다(실측).
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
