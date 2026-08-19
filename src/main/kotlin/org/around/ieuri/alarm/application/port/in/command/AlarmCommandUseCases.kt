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

/**
 * 탈퇴 회원의 알림 데이터 파기(SPEC-0028 R3).
 *
 * 등록·해제와 **포트를 나눈다** — 이 유스케이스만 `AFTER_COMMIT` 리스너가 부르고 그래서
 * 트랜잭션 전파가 다르다. 한 포트에 묶으면 구현도 한 클래스가 되어 전파를 나눌 수 없다.
 */
interface AlarmMemberDataEraseUseCase {
    /** 기기 토큰과 수신 설정을 함께 지운다. */
    fun eraseMemberData(memberId: Long)
}

/** 카테고리별 수신 설정 변경(SPEC-0028 R19). */
interface NotificationPreferenceCommandUseCase {
    /** 반영 후 **두 카테고리 전부**를 돌려준다 — 클라이언트가 재조회 없이 캐시를 간다(AC19.3). */
    fun update(
        command: UpdateNotificationPreferenceCommand,
        memberId: Long,
    ): List<NotificationPreferenceView>
}

/**
 * 푸시 발송(SPEC-0028 R5~R13).
 *
 * 트리거는 각 도메인의 이벤트를 구독한 인바운드 어댑터가 이 포트로 번역해 넣는다 —
 * alarm이 community를 알지 않는 이유다.
 */
interface PushDispatchUseCase {
    /**
     * [recipientId]에게 알림을 보낸다. 수신자 판정(자기 행위 제외·차단·카테고리 게이팅)과
     * 무효 토큰 제거까지 이 안에서 끝난다.
     *
     * **발송하지 않는 것이 정상 결과다** — 토큰이 없거나 차단·설정으로 걸러지면 조용히 끝난다.
     */
    fun dispatch(request: PushDispatchRequest)
}

/**
 * 발송 요청 한 건.
 *
 * **본문이 없다**(R13). 알림에 사용자가 쓴 내용을 싣지 않으므로 발송에 필요한 것은 누구에게
 * 어디로 보낼지가 전부다 — 제목은 목적지의 종류와 기기 언어로 DB에서 찾는다.
 */
data class PushDispatchRequest(
    val recipientId: Long,
    /** 알림을 유발한 사람. [recipientId]와 같으면 발송하지 않는다(R8). */
    val actorId: Long,
    val destination: PushDestination,
)
