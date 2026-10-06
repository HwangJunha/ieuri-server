package org.around.ieuri.tourism.adapter.graphql.support

import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.graphql.acceptLanguage
import org.around.ieuri.tourism.domain.shared.DomainLocale

/**
 * 요청 언어(SPEC-0005 §4.2, POL-039). 헤더 부재·미지원 언어는 EN으로 폴백한다.
 * - 요청 스코프 빈을 쓰지 않는다 — 실행 스레드가 바뀌면 사라진다([acceptLanguage] 참조).
 */
fun DataFetchingEnvironment.domainLocale(): DomainLocale = DomainLocale.fromAcceptLanguage(acceptLanguage())
