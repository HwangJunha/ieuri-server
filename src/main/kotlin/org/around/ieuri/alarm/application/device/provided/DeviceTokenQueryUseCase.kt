package org.around.ieuri.alarm.application.device.provided

import org.around.ieuri.alarm.application.device.provided.dto.DeviceTokenView

/** 발송 대상 기기 조회(SPEC-0028 R5). */
interface DeviceTokenQueryUseCase {
    /** 회원의 기기 전부(AC5.3). 없으면 빈 목록이다. */
    fun tokensOf(memberId: Long): List<DeviceTokenView>
}
