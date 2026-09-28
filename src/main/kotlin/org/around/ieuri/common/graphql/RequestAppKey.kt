package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.context.AppKey

/**
 * 요청한 앱을 **실행 컨텍스트에서** 읽는다(공유 커널). 카테고리 노출 구성이 앱마다 달라
 * tourism·community 양쪽이 이 값을 본다.
 *
 * 인자가 아니라 헤더인 이유는 [acceptLanguage]와 같다 — 요청의 속성이지 클라이언트가
 * 조회마다 고르는 값이 아니다. ThreadLocal을 쓰지 않는 배경도 그쪽에 적어 두었다.
 *
 * 헤더가 없거나 형식이 어긋나면 기본 앱이다.
 */
fun DataFetchingEnvironment.appKey(): AppKey = AppKey.fromHeaderOrDefault(
    DgsContext.getRequestData(this)?.headers?.getFirst(APP_KEY_HEADER),
)

private const val APP_KEY_HEADER = "X-App-Key"
