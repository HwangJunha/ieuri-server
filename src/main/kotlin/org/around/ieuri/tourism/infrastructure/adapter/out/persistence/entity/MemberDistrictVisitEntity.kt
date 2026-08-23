package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema

/**
 * 시군구 방문 기록 JPA 엔티티(SPEC-0030 R3~R5).
 *
 * **시각 컬럼이 없는 것이 이 엔티티의 핵심이다.** 다른 표라면 당연히 있을 `createdAt`이
 * 의도적으로 없다 — 코드에 시각이 붙는 순간 위치 이력이 되어 POL-045의 취지에 반한다(V76).
 *
 * `memberId`는 auth의 것이지만 FK로 묶지 않는다(컨텍스트 간 FK 금지). 탈퇴 시 삭제는
 * 애플리케이션이 한다.
 */
@Entity
@Table(name = "member_district_visit", schema = DbSchema.TOUR)
class MemberDistrictVisitEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,

    @Column(name = "district_code", nullable = false, length = 5)
    var districtCode: String,
)
