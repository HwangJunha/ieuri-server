package org.around.ieuri.auth.infrastructure.adapter.`in`.graphql.mapper

import org.around.ieuri.auth.application.query.MeView
import org.around.ieuri.common.graphql.generated.types.Me as GqlMe
import org.around.ieuri.common.graphql.generated.types.MemberStatus as GqlMemberStatus

/**
 * 본인 정보 read DTO → GraphQL 응답(POL-033).
 *
 * `canChangeEmail`은 read DTO가 provider에서 파생한 값을 그대로 옮긴다 — 리졸버가 provider를
 * 다시 판정하면 규칙이 두 곳에 생긴다(SPEC-0024 R6).
 */
fun MeView.toGql(): GqlMe = GqlMe(
    id = memberId,
    email = email,
    nickname = nickname,
    profileImageUrl = profileImageUrl,
    status = GqlMemberStatus.valueOf(status.name),
    emailVerified = emailVerified,
    canChangeEmail = canChangeEmail,
    roles = roles,
    permissions = permissions,
    createdAt = createdAt.toString(),
)
