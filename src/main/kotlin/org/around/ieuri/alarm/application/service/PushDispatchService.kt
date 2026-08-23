package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchUseCase
import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
import org.around.ieuri.alarm.application.port.out.push.PushSenderPort
import org.around.ieuri.alarm.application.port.out.push.PushTarget
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationMessage
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.PushNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * 푸시 발송(SPEC-0028 R5~R13).
 *
 * 판정 순서는 **싼 것부터**다 — 자기 행위(R8) → 카테고리 설정(R10) → 차단(R9) → 토큰 조회.
 * 앞에서 걸리면 뒤의 조회를 하지 않는다.
 *
 * **이 클래스에는 `@Transactional`이 없다.** FCM 발송이 블로킹 HTTP 왕복이라 트랜잭션 안에
 * 두면 커넥션을 왕복 내내 붙들어, 알림과 무관한 질의까지 커넥션 풀에서 굶는다. DB 접근은
 * [PushDispatchStore]가 짧은 트랜잭션으로 나눠 갖고 발송은 그 사이 트랜잭션 밖에서 일어난다.
 *
 * 기기마다 언어가 다를 수 있어(§5) **언어별로 제목을 골라 발송을 나눈다.**
 */
@Service
class PushDispatchService(
    private val store: PushDispatchStore,
    private val blockLookupPort: BlockLookupPort,
    private val pushSenderPort: PushSenderPort,
) : PushDispatchUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 판정 단계마다 로그를 남긴다. **걸러진 것과 실패한 것이 로그에서 구분되어야 한다** —
     * 둘 다 조용히 끝나므로(R12), 남기지 않으면 "안 왔다"는 신고가 들어왔을 때 어디서
     * 멈췄는지 되짚을 방법이 없다. 실제로 그 진단에 DB 조회가 필요했다.
     *
     * 수신자 id와 기기 식별자까지만 남기고 **토큰 원문은 남기지 않는다**(POL-013).
     */
    override fun dispatch(request: PushDispatchRequest) {
        val type = request.destination.type
        if (request.recipientId == request.actorId) {
            log.debug("푸시 생략(자기 행위): type={} member={}", type, request.recipientId)
            return
        }
        if (!store.categoryEnabled(request.recipientId, request.destination.category)) {
            log.debug("푸시 생략(수신 거부): type={} member={}", type, request.recipientId)
            return
        }
        if (blockLookupPort.isBlockedBetween(request.recipientId, request.actorId)) {
            log.debug("푸시 생략(차단): type={} member={}", type, request.recipientId)
            return
        }

        val tokens = store.findTokens(request.recipientId)
        if (tokens.isEmpty()) {
            log.debug("푸시 생략(등록된 기기 없음): type={} member={}", type, request.recipientId)
            return
        }

        val messages = store.findMessages(type)
        if (messages.isEmpty()) {
            // 문구가 없으면 보낼 것이 없다. 마이그레이션이 3종 × 4언어를 채우므로 정상 상태에서는
            // 일어나지 않는다 — 일어났다면 시드가 지워진 것이라 조용히 넘기지 않는다.
            log.error("알림 문구가 없어 발송하지 못했다: type={}", type)
            return
        }

        // 플랫폼별 내역을 남긴다 — iOS는 FCM이 APNs로 중계하므로 Android와 실패 양상이 다르고,
        // "안 왔다"가 어느 쪽 기기 얘기인지부터 갈라야 원인을 좁힐 수 있다.
        log.info(
            "푸시 발송 시작: type={} member={} devices={} platforms={} languages={}",
            type,
            request.recipientId,
            tokens.size,
            tokens.groupingBy { it.platform }.eachCount(),
            tokens.groupingBy { it.language }.eachCount(),
        )

        // 발송은 트랜잭션 밖이다 — 위 조회는 이미 끝났고 아래 삭제는 아직 시작하지 않았다.
        val invalidDeviceIds = tokens
            .groupBy { it.language }
            .flatMap { (language, group) -> sendTo(language, group, request, messages) }

        store.removeInvalidTokens(invalidDeviceIds)
    }

    /** 한 언어권 기기들로 보낸다. 돌려주는 것은 FCM이 무효로 판정한 기기(R4). */
    private fun sendTo(
        language: AlarmLocale,
        tokens: List<DeviceToken>,
        request: PushDispatchRequest,
        messages: Map<AlarmLocale, NotificationMessage>,
    ): List<String> {
        // 그 언어 문구가 없으면 EN으로 떨어뜨린다(POL-039의 폴백 규약). EN마저 없으면
        // messages가 비어 있다는 뜻인데 그 경우는 호출 전에 걸러진다.
        val message = messages[language] ?: messages[AlarmLocale.EN] ?: return emptyList()
        val notification = PushNotification(
            title = message.title,
            body = message.body,
            destination = request.destination,
        )
        val targets = tokens.map { PushTarget(deviceId = it.deviceId, fcmToken = it.fcmToken) }
        return pushSenderPort.send(notification, targets).invalidDeviceIds
    }
}
