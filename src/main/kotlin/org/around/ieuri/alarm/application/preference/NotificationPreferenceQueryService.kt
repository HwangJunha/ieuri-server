package org.around.ieuri.alarm.application.preference

import org.around.ieuri.alarm.application.preference.provided.NotificationPreferenceQueryUseCase
import org.around.ieuri.alarm.application.preference.provided.dto.NotificationPreferenceView
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceRepository
import org.around.ieuri.alarm.domain.preference.NotificationCategory
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 수신 설정 조회(SPEC-0028 R10·R19). 저장된 행이 없으면 켜짐이다(AC10.3·AC19.1). */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX, readOnly = true)
class NotificationPreferenceQueryService(
    private val repository: NotificationPreferenceRepository,
) : NotificationPreferenceQueryUseCase {

    override fun findByMemberId(memberId: Long): List<NotificationPreferenceView> = NotificationPreference.effective(repository.findByMemberId(memberId))
        .map { (category, enabled) -> NotificationPreferenceView(category, enabled) }

    /** 발송 경로(`@Async`·`AFTER_COMMIT`)에서 불려 REQUIRES_NEW다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW, readOnly = true)
    override fun isEnabled(memberId: Long, category: NotificationCategory): Boolean = repository.findByMemberIdAndCategory(memberId, category)
        ?.enabled
        ?: NotificationPreference.DEFAULT_ENABLED
}
