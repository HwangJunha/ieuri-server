package org.around.ieuri.alarm.application.push

import org.around.ieuri.alarm.application.push.provided.NotificationMessageQueryUseCase
import org.around.ieuri.alarm.application.push.required.NotificationMessageRepository
import org.around.ieuri.alarm.domain.device.AlarmLocale
import org.around.ieuri.alarm.domain.push.NotificationMessage
import org.around.ieuri.alarm.domain.push.NotificationType
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 알림 문구 조회. 발송 경로(`@Async`·`AFTER_COMMIT`)에서 불려 REQUIRES_NEW다. */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class NotificationMessageQueryService(
    private val repository: NotificationMessageRepository,
) : NotificationMessageQueryUseCase {

    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW, readOnly = true)
    override fun messagesOf(type: NotificationType): Map<AlarmLocale, NotificationMessage> = repository.findByType(type).associateBy { it.locale }
}
