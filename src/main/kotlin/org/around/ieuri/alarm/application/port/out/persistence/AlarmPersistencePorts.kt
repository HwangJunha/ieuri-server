package org.around.ieuri.alarm.application.port.out.persistence

import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.NotificationCategory

/**
 * 기기 토큰 저장(SPEC-0028 R1~R4).
 *
 * 조회 키가 `deviceId`인 것이 이 포트의 핵심이다 — FCM 토큰은 로테이션되므로 키가 될 수 없다.
 */
interface DeviceTokenRepositoryPort {
    fun findByDeviceId(deviceId: String): DeviceToken?

    /** 발송 대상 조회. 회원의 기기 전부를 돌려준다(AC5.3). 토큰이 없으면 빈 목록이다. */
    fun findByMemberId(memberId: Long): List<DeviceToken>

    fun save(deviceToken: DeviceToken): DeviceToken

    /** 재등록으로 바뀐 토큰·언어·소유 회원을 반영한다. 관리 엔티티에 얹어 dirty-check로 UPDATE한다. */
    fun update(deviceToken: DeviceToken)

    /** 로그아웃 해제(R2). 없어도 예외가 아니다 — 지운 건수를 돌려준다(AC2.2). */
    fun deleteByDeviceId(deviceId: String): Long

    /** 탈퇴 연쇄 삭제(R3). 지운 건수를 돌려준다. */
    fun deleteByMemberId(memberId: Long): Long

    /** 무효 토큰 제거(R4). 발송 실패 응답으로 판정된 기기만 지운다. */
    fun deleteByDeviceIds(deviceIds: Collection<String>): Long
}

/**
 * 수신 설정 저장(SPEC-0028 R19).
 *
 * **저장된 행이 없는 것이 정상 상태다** — 켜짐을 뜻하며(AC10.3) 사용자가 토글을 처음
 * 건드릴 때만 행이 생긴다.
 */
interface NotificationPreferenceRepositoryPort {
    fun findByMemberId(memberId: Long): List<NotificationPreference>

    fun findByMemberIdAndCategory(
        memberId: Long,
        category: NotificationCategory,
    ): NotificationPreference?

    fun save(preference: NotificationPreference): NotificationPreference

    /** 관리 엔티티에 얹어 dirty-check로 UPDATE한다. */
    fun update(preference: NotificationPreference)

    /** 탈퇴 연쇄 삭제(R3). 지운 건수를 돌려준다. */
    fun deleteByMemberId(memberId: Long): Long
}
