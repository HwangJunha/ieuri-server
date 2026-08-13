package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment
import org.springframework.http.HttpHeaders

/**
 * 요청의 `Accept-Language`를 **실행 컨텍스트에서** 읽는다(공유 커널).
 *
 * **ThreadLocal을 쓰지 않는 것이 이 함수의 존재 이유다.** 이전에는 요청 스코프 빈에 담아
 * 리졸버가 주입받았는데, 그것은 `RequestContextHolder`(ThreadLocal) 기반이라 실행 스레드가
 * 바뀌면 사라진다 — 가상 스레드를 켜자 데이터페처가 다른 스레드로 옮겨가면서 요청 스코프를
 * 쓰는 리졸버가 전부 `ScopeNotActiveException`으로 죽었다(실측).
 *
 * `DataFetchingEnvironment`는 graphql-java가 데이터페처에 **인자로 직접 넘기고**, 헤더는 DGS가
 * 서블릿 스레드에서 미리 캡처해 `DgsContext`에 실어 둔다. 스레드가 몇 번 바뀌어도 그대로 있다.
 *
 * **구독에서도 동작한다** — WebSocket 핸드셰이크 헤더가 같은 자리에 담긴다. 반면
 * `DataFetchingEnvironment.locale`은 구독에서 null로 넘어와 시스템 기본값으로 폴백하므로
 * 쓰지 않는다.
 */
fun DataFetchingEnvironment.acceptLanguage(): String? =
    DgsContext.getRequestData(this)?.headers?.getFirst(HttpHeaders.ACCEPT_LANGUAGE)
