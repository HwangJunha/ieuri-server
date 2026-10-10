package org.around.ieuri.alarm.application.device.provided

import org.around.ieuri.alarm.application.device.provided.dto.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.device.provided.dto.RegisterDeviceTokenCommand

/** 기기 토큰 수명주기(SPEC-0028 R1~R4). */
interface DeviceTokenCommandUseCase {
    /** 등록·토큰 로테이션·언어 변경을 모두 처리하는 업서트(R1·R20). */
    fun register(command: RegisterDeviceTokenCommand, memberId: Long)

    /** 로그아웃 해제. 이미 없어도 성공이다(AC2.2). */
    fun delete(command: DeleteDeviceTokenCommand, memberId: Long)

    /** 탈퇴 회원의 기기 토큰을 지운다(R3). `AFTER_COMMIT` 리스너가 부른다. */
    fun eraseMemberTokens(memberId: Long)

    /** FCM이 무효로 판정한 기기를 지운다(R4). 비어 있으면 아무것도 하지 않는다. */
    fun removeInvalid(deviceIds: List<String>)
}
