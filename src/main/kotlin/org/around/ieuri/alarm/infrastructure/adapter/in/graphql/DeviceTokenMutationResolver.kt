package org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsMutation
import com.netflix.graphql.dgs.InputArgument
import org.around.ieuri.alarm.application.port.`in`.command.DeviceTokenCommandUseCase
import org.around.ieuri.alarm.infrastructure.adapter.`in`.graphql.mapper.toCommand
import org.around.ieuri.common.graphql.generated.types.DeleteDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.DeleteDeviceTokenPayload
import org.around.ieuri.common.graphql.generated.types.RegisterDeviceTokenInput
import org.around.ieuri.common.graphql.generated.types.RegisterDeviceTokenPayload
import org.around.ieuri.common.security.CurrentMemberProvider
import org.springframework.stereotype.Component

/** 기기 토큰 등록·해제(SPEC-0028 R1·R2). 두 연산 모두 인증을 요구한다. */
@DgsComponent
@Component
class DeviceTokenMutationResolver(
    private val deviceTokenCommandUseCase: DeviceTokenCommandUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) {
    @DgsMutation
    fun registerDeviceToken(
        @InputArgument input: RegisterDeviceTokenInput,
    ): RegisterDeviceTokenPayload = withAlarmUserErrors(
        onError = { RegisterDeviceTokenPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        deviceTokenCommandUseCase.register(input.toCommand(), memberId)
        RegisterDeviceTokenPayload(success = true, userErrors = emptyList())
    }

    /** 멱등이다 — 이미 없는 기기를 지워도 성공이다(AC2.2). */
    @DgsMutation
    fun deleteDeviceToken(
        @InputArgument input: DeleteDeviceTokenInput,
    ): DeleteDeviceTokenPayload = withAlarmUserErrors(
        onError = { DeleteDeviceTokenPayload(success = false, userErrors = it) },
    ) {
        val memberId = currentMemberProvider.requireMemberId()
        deviceTokenCommandUseCase.delete(input.toCommand(), memberId)
        DeleteDeviceTokenPayload(success = true, userErrors = emptyList())
    }
}
