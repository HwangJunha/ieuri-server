package org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.alarm.application.port.`in`.command.NotificationPreferenceCommandUseCase
import org.around.ieuri.alarm.application.port.`in`.query.NotificationPreferenceQueryUseCase
import org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper.toGql
import org.around.ieuri.common.graphql.generated.types.UpdateNotificationPreferenceInput
import org.around.ieuri.common.graphql.generated.types.UpdateNotificationPreferencePayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.springframework.stereotype.Component
import org.around.ieuri.common.graphql.generated.types.NotificationPreference as GqlNotificationPreference

/**
 * 카테고리별 수신 설정 조회·변경(SPEC-0028 R19).
 *
 * **루트 쿼리를 만들지 않는다** — 뷰어의 설정이라 이미 있는 `me`의 필드로 읽힌다.
 * 그리고 **지연 필드다**: 이 필드를 선택하지 않은 `me` 조회에는 설정 조회가 붙지 않는다.
 */
@DgsComponent
@Component
class NotificationPreferenceResolver(
    private val queryUseCase: NotificationPreferenceQueryUseCase,
    private val commandUseCase: NotificationPreferenceCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    /**
     * 항상 두 카테고리를 돌려준다 — 저장된 행이 없는 회원도 기본값 켜짐으로 온다(AC19.1).
     *
     * **부모가 준 `MeView`가 아니라 인증 컨텍스트에서 회원을 얻는다.** `me`는 본인만 조회하는
     * 필드라 둘이 같지만, 신원의 출처를 하나로 두는 편이 안전하다.
     */
    @DgsData(parentType = "Me", field = "notificationPreferences")
    fun notificationPreferences(): List<GqlNotificationPreference> {
        val memberId = currentMemberProvider.requireMemberId()
        return queryUseCase.findByMemberId(memberId).map { it.toGql() }
    }

    /** 반영 후 **두 카테고리 전부**를 돌려준다 — 클라이언트가 재조회 없이 캐시를 간다(AC19.3). */
    @DgsMutation
    fun updateNotificationPreference(
        @InputArgument input: UpdateNotificationPreferenceInput,
    ): UpdateNotificationPreferencePayload = withAlarmUserErrors(
        onError = {
            UpdateNotificationPreferencePayload(
                success = false,
                userErrors = it,
                notificationPreferences = emptyList(),
            )
        },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        val preferences = commandUseCase.update(input.toCommand(), memberId)
        UpdateNotificationPreferencePayload(
            success = true,
            userErrors = emptyList(),
            notificationPreferences = preferences.map { it.toGql() },
        )
    }
}
