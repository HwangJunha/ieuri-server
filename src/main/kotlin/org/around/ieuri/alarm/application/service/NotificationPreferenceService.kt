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

/**
 * 카테고리별 수신 설정(SPEC-0028 R19).
 *
 * **저장된 행이 없으면 켜짐이다**(AC10.3·AC19.1). 조회는 항상 두 카테고리를 채워 돌려주고,
 * 저장은 사용자가 토글을 처음 건드릴 때만 일어난다 — 가입 시 기본 행을 만들지 않으므로
 * 기존 회원 백필도 필요 없다.
 */
@Service
class NotificationPreferenceService(
    private val repositoryPort: NotificationPreferenceRepositoryPort,
) : NotificationPreferenceCommandUseCase, NotificationPreferenceQueryUseCase {

    @Transactional(readOnly = true)
    override fun findByMemberId(memberId: Long): List<NotificationPreferenceView> =
        assemble(repositoryPort.findByMemberId(memberId))

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

    /**
     * 저장된 행 위에 **모든 카테고리를 덮어 채운다.** 없는 카테고리는 기본값(켜짐)으로 온다.
     * 반환 순서는 [NotificationCategory]의 선언 순서다.
     */
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
