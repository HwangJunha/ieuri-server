package org.around.ieuri.alarm.application.port.`in`.command

import org.around.ieuri.alarm.application.command.DeleteDeviceTokenCommand
import org.around.ieuri.alarm.application.command.RegisterDeviceTokenCommand
import org.around.ieuri.alarm.application.command.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.query.NotificationPreferenceView
import org.around.ieuri.alarm.domain.vo.PushDestination

/** 기기 토큰 수명주기(SPEC-0028 R1·R2). */
interface DeviceTokenCommandUseCase {
    /** 등록·토큰 로테이션·언어 변경을 모두 처리하는 업서트(R1·R20). */
    fun register(command: RegisterDeviceTokenCommand, memberId: Long)

    /** 로그아웃 해제. 이미 없어도 성공이다(AC2.2). */
    fun delete(command: DeleteDeviceTokenCommand, memberId: Long)
}

/** 탈퇴 회원의 알림 데이터 파기(SPEC-0028 R3). `AFTER_COMMIT` 리스너가 부른다. */
interface AlarmMemberDataEraseUseCase {
    /** 기기 토큰과 수신 설정을 함께 지운다. */
    fun eraseMemberData(memberId: Long)
}

/** 카테고리별 수신 설정 변경(SPEC-0028 R19). */
interface NotificationPreferenceCommandUseCase {
    /** 반영 후 두 카테고리 전부를 돌려준다(AC19.3). */
    fun update(
        command: UpdateNotificationPreferenceCommand,
        memberId: Long,
    ): List<NotificationPreferenceView>
}

/** 푸시 발송(SPEC-0028 R5~R13). 다른 도메인의 이벤트를 인바운드 어댑터가 번역해 넣는다. */
interface PushDispatchUseCase {
    /**
     * 수신자에게 알림을 보낸다. 자기 행위 제외·차단·카테고리 게이팅과 무효 토큰 제거까지 한다.
     * - 걸러져 발송하지 않아도 예외 없이 조용히 끝난다.
     */
    fun dispatch(request: PushDispatchRequest)
}

/** 발송 요청 한 건. 본문은 없고, 문구는 목적지 종류와 기기 언어로 DB에서 찾는다(R13). */
data class PushDispatchRequest(
    val recipientId: Long,
    /** 알림을 유발한 사람. [recipientId]와 같으면 발송하지 않는다(R8). */
    val actorId: Long,
    val destination: PushDestination,
)
