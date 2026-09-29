package org.around.ieuri.alarm.application.service

import org.around.ieuri.alarm.application.command.UpdateNotificationPreferenceCommand
import org.around.ieuri.alarm.application.port.`in`.command.NotificationPreferenceCommandUseCase
import org.around.ieuri.alarm.application.port.`in`.query.NotificationPreferenceQueryUseCase
import org.around.ieuri.alarm.application.port.out.persistence.NotificationPreferenceRepositoryPort
import org.around.ieuri.alarm.application.query.NotificationPreferenceView
import org.around.ieuri.alarm.domain.aggregate.NotificationPreference
import org.around.ieuri.alarm.domain.vo.NotificationCategory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** 카테고리별 수신 설정(SPEC-0028 R19). 저장된 행이 없으면 켜짐이다(AC10.3·AC19.1). */
@Service
class NotificationPreferenceService(
    private val repositoryPort: NotificationPreferenceRepositoryPort,
) : NotificationPreferenceCommandUseCase,
    NotificationPreferenceQueryUseCase {

    @Transactional(readOnly = true)
    override fun findByMemberId(memberId: Long): List<NotificationPreferenceView> = assemble(repositoryPort.findByMemberId(memberId))

    @Transactional
    override fun update(
        command: UpdateNotificationPreferenceCommand,
        memberId: Long,
    ): List<NotificationPreferenceView> {
        val existing = repositoryPort.findByMemberIdAndCategory(memberId, command.category)
        if (existing == null) {
            repositoryPort.save(
                NotificationPreference.create(memberId, command.category, command.enabled),
            )
        } else {
            existing.change(command.enabled)
            repositoryPort.update(existing)
        }
        return assemble(repositoryPort.findByMemberId(memberId))
    }

    /** 모든 카테고리를 채워 돌려준다. 없는 카테고리는 켜짐, 순서는 [NotificationCategory] 선언 순이다. */
    private fun assemble(stored: List<NotificationPreference>): List<NotificationPreferenceView> {
        val byCategory = stored.associateBy { it.category }
        return NotificationCategory.entries.map { category ->
            NotificationPreferenceView(
                category = category,
                enabled = byCategory[category]?.enabled ?: NotificationPreference.DEFAULT_ENABLED,
            )
        }
    }
}
