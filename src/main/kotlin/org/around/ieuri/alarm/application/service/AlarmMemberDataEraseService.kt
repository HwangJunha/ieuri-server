package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.`in`.command.AlarmMemberDataEraseUseCase
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 탈퇴 회원의 알림 데이터 파기(SPEC-0028 R3, POL-009).
 *
 * **기기 토큰 등록·해제와 한 클래스에 두지 않는다.** 이 유스케이스만 `REQUIRES_NEW`가
 * 필요한데(아래), 같은 클래스에 두면 일반 요청 경로인 등록·해제까지 새 트랜잭션을 열게 되고
 * 그것은 호출자의 트랜잭션과 분리돼 롤백이 갈린다. community가 `MemberDataEraseService`를
 * 따로 둔 것과 같은 이유다.
 */
@Service
class AlarmMemberDataEraseService(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
    private val notificationPreferenceRepositoryPort: NotificationPreferenceRepositoryPort,
) : AlarmMemberDataEraseUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **REQUIRES_NEW여야 한다.** `AFTER_COMMIT` 리스너가 부르는데, 그 시점의 원본 트랜잭션은
     * 이미 커밋됐으면서도 스레드에 묶여 있다. 기본 전파(REQUIRED)로 두면 커밋할 수 없는 그
     * 트랜잭션에 조용히 합류해 삭제가 흘러가지 않는다 — 예외도 나지 않아 성공한 것처럼 보인다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberData(memberId: Long) {
        val tokens = deviceTokenRepositoryPort.deleteByMemberId(memberId)
        val preferences = notificationPreferenceRepositoryPort.deleteByMemberId(memberId)
        // 토큰 원문을 남기지 않는다(POL-013) — 식별자와 건수만.
        log.info(
            "alarm data erased for withdrawn member {}: deviceTokens={} preferences={}",
            memberId,
            tokens,
            preferences,
        )
    }
}
