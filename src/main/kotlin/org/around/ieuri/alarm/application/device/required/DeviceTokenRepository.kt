package org.around.ieuri.alarm.application.device.required

import org.around.ieuri.alarm.domain.device.DeviceToken

/** 기기 토큰 저장(SPEC-0028 R1~R4). 키는 로테이션되는 FCM 토큰이 아니라 `deviceId`다. */
interface DeviceTokenRepository {
    fun findByDeviceId(deviceId: String): DeviceToken?

    /** 회원의 기기 전부(AC5.3). 토큰이 없으면 빈 목록이다. */
    fun findByMemberId(memberId: Long): List<DeviceToken>

    /** id가 없으면 넣는다. 있으면 값이 바뀐 경우에만 고친다(`updated_at` 포함). */
    fun save(deviceToken: DeviceToken)

    /** 지운 건수. 없어도 예외가 아니다(AC2.2). */
    fun deleteByDeviceId(deviceId: String): Long

    fun deleteByMemberId(memberId: Long): Long

    fun deleteByDeviceIds(deviceIds: Collection<String>): Long
}
