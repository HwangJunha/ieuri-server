package org.around.ieuri.community.infrastructure.locale

import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.graphql.acceptLanguage
import org.around.ieuri.community.domain.vo.ContentLanguage

/**
 * 요청 언어. 헤더 부재·미지원 언어는 폴백(EN)이다.
 *
 * **요청 스코프 빈을 쓰지 않는다** — 그것은 ThreadLocal 기반이라 실행 스레드가 바뀌면 사라진다.
 * 자세한 배경은 [acceptLanguage].
 */
fun DataFetchingEnvironment.contentLanguage(): ContentLanguage =
    ContentLanguage.fromAcceptLanguage(acceptLanguage())
