package org.around.ieuri.common.graphql

import com.netflix.graphql.dgs.context.DgsContext
import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.context.AppKey

/**
 * 요청한 앱(`X-App-Key` 헤더)을 실행 컨텍스트에서 읽는다. 헤더가 없거나 형식이 어긋나면 기본 앱이다.
 */
fun DataFetchingEnvironment.appKey(): AppKey = AppKey.fromHeaderOrDefault(
    DgsContext.getRequestData(this)?.headers?.getFirst(APP_KEY_HEADER),
)

private const val APP_KEY_HEADER = "X-App-Key"
