package org.around.ieuri.alarm.application.preference

import org.around.ieuri.alarm.application.preference.provided.NotificationPreferenceCommandUseCase
import org.around.ieuri.alarm.application.preference.provided.dto.NotificationPreferenceView
import org.around.ieuri.alarm.application.preference.provided.dto.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.preference.required.NotificationPreferenceRepository
import org.around.ieuri.alarm.domain.preference.NotificationPreference
import org.around.ieuri.common.persistence.ExposedConfig
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/** 카테고리별 수신 설정 변경·파기(SPEC-0028 R3·R19). */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class NotificationPreferenceCommandService(
    private val repository: NotificationPreferenceRepository,
) : NotificationPreferenceCommandUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun update(
        command: UpdateNotificationPreferenceCommand,
        memberId: Long,
    ): List<NotificationPreferenceView> {
        val existing = repository.findByMemberIdAndCategory(memberId, command.category)
        repository.save(existing?.change(command.enabled) ?: NotificationPreference.create(memberId, command.category, command.enabled))
        return NotificationPreference.effective(repository.findByMemberId(memberId)).map { (category, enabled) -> NotificationPreferenceView(category, enabled) }
    }

    /** `AFTER_COMMIT` 리스너가 부르므로 REQUIRES_NEW여야 한다 — REQUIRED면 삭제가 조용히 사라진다. */
    @Transactional(transactionManager = ExposedConfig.EXPOSED_TX, propagation = Propagation.REQUIRES_NEW)
    override fun eraseMemberPreferences(memberId: Long) {
        val preferences = repository.deleteByMemberId(memberId)
        log.info("alarm preferences erased for withdrawn member {}: preferences={}", memberId, preferences)
    }
}
