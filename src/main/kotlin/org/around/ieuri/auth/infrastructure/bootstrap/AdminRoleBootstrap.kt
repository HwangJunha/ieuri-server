package org.around.ieuri.auth.infrastructure.bootstrap

import org.around.ieuri.auth.application.port.out.persistence.MemberRepositoryPort
import org.around.ieuri.auth.application.port.out.persistence.RoleRepositoryPort
import org.around.ieuri.auth.domain.aggregate.Role
import org.around.ieuri.auth.infrastructure.config.RbacProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 최초 ADMIN 부트스트랩. 부팅 시 설정(ieuri.rbac.bootstrap-admin-member-ids)의 회원들에게 ADMIN 역할을
 * 멱등하게 부여한다. 데이터 시드(R__seed_rbac)로 역할/권한은 준비되므로, 여기서는 "누가 관리자인가"만 연결한다.
 * 목록이 비어 있으면 아무 것도 하지 않는다(운영은 환경변수로 주입).
 */
@Component
class AdminRoleBootstrap(
    private val properties: RbacProperties,
    private val memberRepository: MemberRepositoryPort,
    private val roleRepository: RoleRepositoryPort,
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(AdminRoleBootstrap::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val ids = properties.bootstrapAdminMemberIds
        if (ids.isEmpty()) return

        for (memberId in ids.distinct()) {
            if (!memberRepository.existsById(memberId)) {
                log.warn("ADMIN 부트스트랩 건너뜀 — 회원 없음(memberId={})", memberId)
                continue
            }
            val assigned = roleRepository.assignRoleToMember(memberId, Role.ADMIN)
            if (assigned) {
                log.info("ADMIN 역할 부여(memberId={})", memberId)
            }
        }
    }
}
