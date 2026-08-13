package org.around.ieuri.tourism.infrastructure.locale

import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.graphql.acceptLanguage
import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 요청 언어(SPEC-0005 §4.2, POL-039). 헤더 부재·미지원 언어는 폴백(EN)이다.
 *
 * **요청 스코프 빈을 쓰지 않는다** — 그것은 ThreadLocal 기반이라 실행 스레드가 바뀌면 사라진다.
 * 자세한 배경은 [acceptLanguage].
 */
fun DataFetchingEnvironment.domainLocale(): DomainLocale =
    DomainLocale.fromAcceptLanguage(acceptLanguage())
