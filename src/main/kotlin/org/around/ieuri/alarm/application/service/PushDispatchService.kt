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
 * 푸시 발송(SPEC-0028 R5~R13). 판정 순서: 자기 행위(R8) → 카테고리(R10) → 차단(R9) → 토큰.
 * - `@Transactional`을 두지 않는다. DB 접근은 [PushDispatchStore]가, FCM 발송은 트랜잭션 밖에서 한다.
 * - 기기 언어별로 문구를 골라 발송을 나눈다.
 */
@Service
class PushDispatchService(
    private val store: PushDispatchStore,
    private val blockLookupPort: BlockLookupPort,
    private val pushSenderPort: PushSenderPort,
) : PushDispatchUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /** 판정 단계마다 생략 사유를 로그로 남긴다. 토큰 원문은 남기지 않는다(POL-013). */
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
            // 마이그레이션 시드가 지워진 상태다 — error로 남긴다.
            log.error("알림 문구가 없어 발송하지 못했다: type={}", type)
            return
        }

        log.info(
            "푸시 발송 시작: type={} member={} devices={} platforms={} languages={}",
            type,
            request.recipientId,
            tokens.size,
            tokens.groupingBy { it.platform }.eachCount(),
            tokens.groupingBy { it.language }.eachCount(),
        )

        // 발송은 트랜잭션 밖이다.
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
        // 그 언어 문구가 없으면 EN으로 폴백한다(POL-039).
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
