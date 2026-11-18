package org.around.ieuri.common.security

import graphql.schema.DataFetchingEnvironment

/**
 * 새 구독을 열 자격이 있는지 판정한다(SPEC-0032 R7, POL-048). auth가 구현한다.
 * - 이미 열린 구독 스트림은 대상이 아니다 — 새 `subscribe`에만 적용한다.
 */
interface SubscriptionAuthorizer {

    /** 자격이 없으면 [SubscriptionUnauthenticatedException]을 던진다. HTTP 요청에서는 아무 것도 하지 않는다. */
    fun requireLiveIdentity(dfe: DataFetchingEnvironment, memberId: Long)
}
