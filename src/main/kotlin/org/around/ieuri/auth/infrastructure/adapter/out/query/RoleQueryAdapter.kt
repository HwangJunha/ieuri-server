package org.around.ieuri.auth.infrastructure.adapter.out.query

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.auth.application.port.out.query.RoleQueryPort
import org.around.ieuri.auth.application.query.RoleInfoView
import org.around.ieuri.auth.infrastructure.adapter.out.persistence.entity.QRoleEntity
import org.springframework.stereotype.Component

/**
 * 역할 읽기 포트 구현(CQRS 읽기 측). QueryDSL이 role 테이블을 read DTO로 직접 프로젝션한다.
 * 부여 가능한 역할 = 시드/관리로 등록된 role 전체(데이터주도). 코드 순으로 정렬한다.
 */
@Component
class RoleQueryAdapter(
    private val queryFactory: JPAQueryFactory,
) : RoleQueryPort {

    private val role = QRoleEntity.roleEntity

    override fun findAssignableRoles(): List<RoleInfoView> = queryFactory
        .select(role.code, role.name, role.description)
        .from(role)
        .orderBy(role.code.asc())
        .fetch()
        .map { RoleInfoView(requireNotNull(it.get(role.code)), requireNotNull(it.get(role.name)), it.get(role.description)) }
}
