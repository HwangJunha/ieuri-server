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

/** 카테고리별 수신 설정 조회(`Me.notificationPreferences` 지연 필드)·변경(SPEC-0028 R19). */
@DgsComponent
@Component
class NotificationPreferenceResolver(
    private val queryUseCase: NotificationPreferenceQueryUseCase,
    private val commandUseCase: NotificationPreferenceCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    /** 항상 두 카테고리를 돌려준다. 저장된 행이 없으면 켜짐이다(AC19.1). */
    @DgsData(parentType = "Me", field = "notificationPreferences")
    fun notificationPreferences(): List<GqlNotificationPreference> {
        val memberId = currentMemberProvider.requireMemberId()
        return queryUseCase.findByMemberId(memberId).map { it.toGql() }
    }

    /** 반영 후 두 카테고리 전부를 돌려준다(AC19.3). */
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
