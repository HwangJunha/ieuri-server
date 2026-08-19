package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchRequest
import org.around.ieuri.alarm.application.port.`in`.command.PushDispatchUseCase
import org.around.ieuri.alarm.application.port.out.client.BlockLookupPort
import org.around.ieuri.alarm.application.port.out.client.MemberNicknameLookupPort
import org.around.ieuri.alarm.application.port.out.persistence.DeviceTokenRepositoryPort
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.application.port.out.push.PushSenderPort
import org.around.ieuri.alarm.application.port.out.push.PushTarget
import org.around.ieuri.alarm.domain.aggregate.DeviceToken
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.AlarmLocale
import org.around.ieuri.alarm.domain.vo.PushDestination
import org.around.ieuri.alarm.domain.vo.PushMessages
import org.around.ieuri.alarm.domain.vo.PushNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 푸시 발송(SPEC-0028 R5~R13).
 *
 * 판정 순서는 **싼 것부터**다 — 자기 행위(R8) → 카테고리 설정(R10) → 차단(R9) → 토큰 조회.
 * 앞에서 걸리면 뒤의 조회를 하지 않는다.
 *
 * 기기마다 언어가 다를 수 있어(§5) **제목은 언어별로 조립하고 발송을 나눈다.** 본문은
 * 언어와 무관하게 원문 그대로다(R13).
 */
@Service
class PushDispatchService(
    private val deviceTokenRepositoryPort: DeviceTokenRepositoryPort,
    private val preferenceRepositoryPort: NotificationPreferenceRepositoryPort,
    private val blockLookupPort: BlockLookupPort,
    private val memberNicknameLookupPort: MemberNicknameLookupPort,
    private val pushSenderPort: PushSenderPort,
) : PushDispatchUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * **REQUIRES_NEW여야 한다.** `AFTER_COMMIT` 리스너가 부르는데 그 시점의 원본 트랜잭션은
     * 이미 커밋됐으면서도 스레드에 묶여 있다 — 기본 전파로 두면 무효 토큰 삭제가 조용히
     * 사라진다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun dispatch(request: PushDispatchRequest) {
        if (request.recipientId == request.actorId) return
        if (!categoryEnabled(request)) return
        if (blockLookupPort.isBlockedBetween(request.recipientId, request.actorId)) return

        val tokens = deviceTokenRepositoryPort.findByMemberId(request.recipientId)
        if (tokens.isEmpty()) return

        val body = PushMessages.truncateBody(request.body)
        val invalidDeviceIds = tokens
            .groupBy { it.language }
            .flatMap { (language, group) -> sendTo(language, group, request, body) }

        if (invalidDeviceIds.isNotEmpty()) {
            val removed = deviceTokenRepositoryPort.deleteByDeviceIds(invalidDeviceIds)
            log.info("invalid device tokens removed: count={}", removed)
        }
    }

    /** 한 언어권 기기들로 보낸다. 돌려주는 것은 FCM이 무효로 판정한 기기(R4). */
    private fun sendTo(
        language: AlarmLocale,
        tokens: List<DeviceToken>,
        request: PushDispatchRequest,
        body: String,
    ): List<String> {
        val notification = PushNotification(
            title = title(request.destination, language, request.actorId),
            body = body,
            destination = request.destination,
        )
        val targets = tokens.map { PushTarget(deviceId = it.deviceId, fcmToken = it.fcmToken) }
        return pushSenderPort.send(notification, targets).invalidDeviceIds
    }

    /**
     * 제목 문구(R13 — 서버가 조립하는 문구만 번역한다).
     *
     * DM은 보낸 사람 닉네임을 넣는다. 닉네임을 못 읽으면 이름 없는 제목으로 떨어뜨린다 —
     * 알림 자체를 거르는 것보다 낫다.
     */
    private fun title(
        destination: PushDestination,
        language: AlarmLocale,
        actorId: Long,
    ): String = when (destination) {
        is PushDestination.Dm ->
            memberNicknameLookupPort.findNickname(actorId)
                ?.let { PushMessages.dmTitle(language, it) }
                ?: PushMessages.dmTitleWithoutSender(language)
        is PushDestination.SpotQaAnswer -> PushMessages.answerTitle(language)
        is PushDestination.SpotQaReply -> PushMessages.replyTitle(language)
    }

    /** 저장된 행이 없으면 켜진 것으로 본다(AC10.3). */
    private fun categoryEnabled(request: PushDispatchRequest): Boolean =
        preferenceRepositoryPort
            .findByMemberIdAndCategory(request.recipientId, request.destination.category)
            ?.enabled
            ?: NotificationPreference.DEFAULT_ENABLED
}
