package org.around.ieuri.auth.application

import org.around.ieuri.auth.application.command.AssignRoleCommand
import org.around.ieuri.auth.application.command.RevokeRoleCommand
import org.around.ieuri.auth.application.exception.MemberNotFoundException
import org.around.ieuri.auth.application.exception.RoleNotFoundException
import org.around.ieuri.auth.application.exception.SelfDemotionException
import org.around.ieuri.auth.application.port.`in`.command.RoleChangeResult
import org.around.ieuri.auth.application.port.`in`.command.RoleUseCase
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 역할 부여/회수(관리자). member_role 매핑을 추가/제거하고 감사 이력을 남긴다. 둘 다 멱등이다.
 * 대상 회원의 accessToken에는 다음 재발급(최대 access TTL) 후 새 권한이 반영된다(즉시 무효화는 이월 스텝).
 *
 * 자기 강등 방지: 본인이 자신의 마지막 "관리 역할"(role:assign/role:revoke를 주는 역할)을 회수하는 것은 거부한다.
 */
@Service
class RoleHandler(
    private val memberRepository: MemberRepositoryPort,
    private val roleRepository: RoleRepositoryPort,
    private val auditLog: AuditLogPort,
) : RoleUseCase {

    @Transactional
    override fun assign(command: AssignRoleCommand): RoleChangeResult {
        if (!memberRepository.existsById(command.targetMemberId)) {
            throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        }
        roleRepository.findByCode(command.roleCode)
            ?: throw RoleNotFoundException("존재하지 않는 역할입니다: ${command.roleCode}")

        val changed = roleRepository.assignRoleToMember(command.targetMemberId, command.roleCode)
        if (changed) {
            auditLog.record(
                memberId = command.targetMemberId,
                eventType = "MEMBER_ROLE_ASSIGNED",
                payload = mapOf("roleCode" to command.roleCode, "actorMemberId" to command.actorMemberId),
            )
        }
        return RoleChangeResult(success = true, changed = changed)
    }

    @Transactional
    override fun revoke(command: RevokeRoleCommand): RoleChangeResult {
        if (!memberRepository.existsById(command.targetMemberId)) {
            throw MemberNotFoundException("회원을 찾을 수 없습니다.")
        }
        val role = roleRepository.findByCode(command.roleCode)
            ?: throw RoleNotFoundException("존재하지 않는 역할입니다: ${command.roleCode}")

        if (command.actorMemberId == command.targetMemberId && role.isAdministrative()) {
            val retainsAnotherAdminRole = roleRepository.findRolesOfMember(command.targetMemberId)
                .any { it.code != role.code && it.isAdministrative() }
            if (!retainsAnotherAdminRole) {
                throw SelfDemotionException("마지막 관리 역할은 스스로 회수할 수 없습니다.")
            }
            // 참고: 이 검사(조회)와 실제 회수(삭제) 사이에는 이론적 TOCTOU 경합이 있다.
            // 본인의 마지막 관리 역할 2개를 두 요청이 동시에 회수하면 둘 다 통과할 수 있다.
            // RBAC 변경은 관리자 전용·저빈도이고 즉시 무효화 자체가 Valkey 스텝으로 이월된 상태라,
            // 행 잠금(SELECT ... FOR UPDATE)을 도입하지 않고 이 잔여 위험을 의도적으로 수용한다.
        }

        val changed = roleRepository.revokeRoleFromMember(command.targetMemberId, command.roleCode)
        if (changed) {
            auditLog.record(
                memberId = command.targetMemberId,
                eventType = "MEMBER_ROLE_REVOKED",
                payload = mapOf("roleCode" to command.roleCode, "actorMemberId" to command.actorMemberId),
            )
        }
        return RoleChangeResult(success = true, changed = changed)
    }
}
