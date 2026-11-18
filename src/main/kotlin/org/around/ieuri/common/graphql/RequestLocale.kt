package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment
import org.springframework.http.HttpHeaders

/**
 * 요청의 `Accept-Language`를 실행 컨텍스트(DgsContext)에서 읽는다 — ThreadLocal을 쓰지 않는다(가상 스레드).
 * - 구독에서도 동작한다. `DataFetchingEnvironment.locale`은 구독에서 null이라 쓰지 않는다.
 */
fun DataFetchingEnvironment.acceptLanguage(): String? = DgsContext.getRequestData(this)?.headers?.getFirst(HttpHeaders.ACCEPT_LANGUAGE)
