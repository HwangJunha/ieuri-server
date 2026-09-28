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
 * - 시각 컬럼을 두지 않는다 — 시각이 붙으면 위치 이력이 된다(POL-045, V76).
 * - `memberId`는 FK가 아니다. 탈퇴 시 삭제는 애플리케이션이 한다.
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
