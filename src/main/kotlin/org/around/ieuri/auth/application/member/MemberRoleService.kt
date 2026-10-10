package org.around.ieuri.auth.application.member

import org.around.ieuri.auth.application.member.provided.MemberRoleUseCase
import org.around.ieuri.auth.application.member.provided.dto.AssignRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RevokeRoleCommand
import org.around.ieuri.auth.application.member.provided.dto.RoleChangeResult
import org.around.ieuri.auth.application.member.provided.exception.MemberNotFoundException
import org.around.ieuri.auth.application.member.provided.exception.RoleNotFoundException
import org.around.ieuri.auth.application.member.required.MemberEventPublisherPort
import org.around.ieuri.auth.application.member.required.MemberRepository
import org.around.ieuri.auth.domain.member.Member
import org.around.ieuri.auth.domain.member.MemberRoleChange
import org.around.ieuri.auth.domain.member.Role
import org.around.ieuri.auth.domain.member.event.MemberRoleGranted
import org.around.ieuri.auth.domain.member.event.MemberRoleRevoked
import org.around.ieuri.common.persistence.ExposedConfig
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock

/**
 * 역할 부여·회수(관리자). 둘 다 멱등이다.
 * - 대상의 accessToken에는 다음 재발급(최대 access TTL) 뒤에 반영된다.
 * - 자기 강등 검사와 회수 사이의 경합(동시 회수 두 건이 모두 통과)은 받아들인 잔여 위험이다.
 */
@Service
@Transactional(transactionManager = ExposedConfig.EXPOSED_TX)
class MemberRoleService(
    private val memberRepository: MemberRepository,
    private val eventPublisher: MemberEventPublisherPort,
    private val clock: Clock,
) : MemberRoleUseCase {

    override fun assign(command: AssignRoleCommand): RoleChangeResult {
        val member = load(command.targetMemberId)
        val role = loadRole(command.roleCode)
        val change = member.grantRole(role, memberRepository.findRolesOf(command.targetMemberId))
        if (change is MemberRoleChange.Granted) {
            memberRepository.addRole(change, clock.instant())
            eventPublisher.publish(MemberRoleGranted(command.targetMemberId, command.roleCode, command.actorMemberId))
        }
        return RoleChangeResult(success = true, changed = change !is MemberRoleChange.Unchanged)
    }

    override fun revoke(command: RevokeRoleCommand): RoleChangeResult {
        val member = load(command.targetMemberId)
        val role = loadRole(command.roleCode)
        val change = member.revokeRole(role, memberRepository.findRolesOf(command.targetMemberId), command.actorMemberId)
        if (change is MemberRoleChange.Revoked) {
            memberRepository.removeRole(change)
            eventPublisher.publish(MemberRoleRevoked(command.targetMemberId, command.roleCode, command.actorMemberId))
        }
        return RoleChangeResult(success = true, changed = change !is MemberRoleChange.Unchanged)
    }

    private fun load(memberId: Long): Member = memberRepository.findById(memberId)
        ?: throw MemberNotFoundException("회원을 찾을 수 없습니다.")

    private fun loadRole(code: String): Role = memberRepository.findRole(code)
        ?: throw RoleNotFoundException("존재하지 않는 역할입니다: $code")
}
