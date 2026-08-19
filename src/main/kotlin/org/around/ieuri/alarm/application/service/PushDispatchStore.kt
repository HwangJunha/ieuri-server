package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 발송 경로의 DB 접근만 담는다(SPEC-0028).
 *
 * **[PushDispatchService]에서 분리한 이유는 트랜잭션 경계다.** 발송은 FCM으로 나가는 블로킹
 * HTTP 왕복인데, 그것을 트랜잭션 안에 두면 커넥션 하나를 왕복 내내 붙든다 — 커넥션 풀이
 * 10개라 FCM이 느려지는 순간 알림과 무관한 질의까지 함께 막힌다.
 *
 * 그래서 읽기와 쓰기를 각각 짧은 트랜잭션으로 잘라 여기 두고, 그 사이의 발송은 트랜잭션
 * 밖에서 일어난다. **같은 클래스의 private 메서드로 나누면 안 된다** — 자기 호출은 프록시를
 * 지나지 않아 `@Transactional`이 조용히 무효가 된다.
 *
 * `REQUIRES_NEW`는 **오늘의 경로에서는 아무 일도 하지 않는다.** 호출자인 리스너가 `@Async`라
 * 실행이 발송 워커 스레드로 넘어가고, 거기에는 묶인 트랜잭션이 없어 중단할 것이 없다.
 *
 * 그럼에도 두는 이유는 `@Async`가 빠지는 경우에 대한 대비다. 그러면 호출자가 `AFTER_COMMIT`
 * 리스너의 스레드가 되는데, 그 시점의 원본 트랜잭션은 이미 커밋됐으면서도 스레드에 묶여
 * 있어 기본 전파로는 커밋할 수 없는 그 트랜잭션에 합류해 **쓰기가 조용히 사라진다.**
 * `LayeredArchitectureTest`가 이 전파를 강제한다.
 */
@Service
class PushDispatchStore(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
    private val preferenceRepositoryPort: NotificationPreferenceRepositoryPort,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** 저장된 행이 없으면 켜진 것으로 본다(AC10.3). */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun categoryEnabled(memberId: Long, category: NotificationCategory): Boolean =
        preferenceRepositoryPort
            .findByMemberIdAndCategory(memberId, category)
            ?.enabled
            ?: NotificationPreference.DEFAULT_ENABLED

    /** 수신자의 기기 전부(AC5.3). 없으면 빈 목록이다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun findTokens(memberId: Long): List<DeviceToken> =
        deviceTokenRepositoryPort.findByMemberId(memberId)

    /** FCM이 무효로 판정한 기기를 지운다(R4). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun removeInvalidTokens(deviceIds: List<String>) {
        if (deviceIds.isEmpty()) return
        val removed = deviceTokenRepositoryPort.deleteByDeviceIds(deviceIds)
        // 토큰 원문을 남기지 않는다(POL-013) — 건수만.
        log.info("invalid device tokens removed: count={}", removed)
    }
}
