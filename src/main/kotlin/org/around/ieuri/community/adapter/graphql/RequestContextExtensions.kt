package org.around.ieuri.community.adapter.graphql

import graphql.schema.DataFetchingEnvironment
import org.around.ieuri.common.graphql.acceptLanguage
import org.around.ieuri.community.domain.shared.ContentLanguage

/**
 * 요청 언어. 헤더 부재·미지원 언어는 폴백(EN)이다.
 * - 요청 스코프 빈(ThreadLocal)으로 바꾸지 않는다 — 실행 스레드가 바뀌면 사라진다.
 */
fun DataFetchingEnvironment.contentLanguage(): ContentLanguage = ContentLanguage.fromAcceptLanguage(acceptLanguage())
