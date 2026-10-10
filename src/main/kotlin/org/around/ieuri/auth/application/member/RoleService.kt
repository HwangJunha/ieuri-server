package org.around.ieuri.auth.application.member
import org.around.ieuri.auth.application.member.provided.RoleUseCase
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.RoleNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.SelfDemotionException
import org.around.ieuri.auth.application.port.out.persistence.AuditLogPort
import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 역할 부여/회수(관리자). 둘 다 멱등이며 감사 이력을 남긴다.
 * - 대상의 accessToken에는 다음 재발급(최대 access TTL) 후 반영된다.
 * - 본인의 마지막 관리 역할(role:assign/role:revoke를 주는 역할) 회수는 거부한다(자기 강등 방지).
 */
@Service
class RoleService(
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
            // 검사와 회수 사이의 TOCTOU 경합(동시 회수 2건이 모두 통과)은 의도적으로 수용한 잔여 위험이다.
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
