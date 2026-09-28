package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.AreaEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.DistrictEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellDistrictEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellDistrictId
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapCellImageEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapRelationEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MapShareLinkEntity
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.MemberDistrictVisitEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying

/** 시군구 방문 기록 레포지토리(SPEC-0030 R3~R6). 등록(upsert)은 전용 JDBC 어댑터가 한다. */
interface MemberDistrictVisitJpaRepository : JpaRepository<MemberDistrictVisitEntity, Long> {

    fun findAllByMemberId(memberId: Long): List<MemberDistrictVisitEntity>

    /** 벌크 삭제라 영속성 컨텍스트를 우회한다 — 같은 트랜잭션에서 이 엔티티를 다시 읽지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByMemberIdAndDistrictCodeIn(memberId: Long, districtCodes: Collection<String>): Int

    /** 탈퇴 시 호출된다 — 방문 기록은 계정과 함께 사라진다(§9.1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByMemberId(memberId: Long): Int
}

/** 공유 링크 레포지토리(SPEC-0030 R12~R14). 발급(findOrCreate)은 전용 JDBC 어댑터가 한다. */
interface MapShareLinkJpaRepository : JpaRepository<MapShareLinkEntity, Long> {

    /** 유효한 링크만. 폐기됐거나 없으면 null이다(둘을 구분하지 않는다). */
    fun findByTokenAndRevokedAtIsNull(token: String): MapShareLinkEntity?

    /** 본인 것이 아니거나 이미 폐기됐으면 null이다. */
    fun findByMemberIdAndTokenAndRevokedAtIsNull(memberId: Long, token: String): MapShareLinkEntity?

    /** 탈퇴 시 호출된다 — 폐기가 아니라 삭제다(§9.1). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByMemberId(memberId: Long): Int
}

/** 칸 대표 이미지 레포지토리(SPEC-0030 R9·R10). */
interface MapCellImageJpaRepository : JpaRepository<MapCellImageEntity, Long> {

    /** 대표 이미지는 `display_order = 0`이다. 없는 칸은 결과에 없다(R10). */
    fun findAllByDisplayOrder(displayOrder: Int): List<MapCellImageEntity>
}

/** 시도 레포지토리(KTO 원본 17행). */
interface AreaJpaRepository : JpaRepository<AreaEntity, String>

/** 시군구 레포지토리(KTO 원본 252행). 공간 질의는 생 SQL 어댑터가 한다(`boundary`·`center` 미매핑). */
interface DistrictJpaRepository : JpaRepository<DistrictEntity, String> {

    fun findAllByCodeIn(codes: Collection<String>): List<DistrictEntity>
}

/** 칸 ↔ 시군구 매핑 레포지토리. */
interface MapCellDistrictJpaRepository : JpaRepository<MapCellDistrictEntity, MapCellDistrictId> {

    fun findAllByDistrictCodeIn(districtCodes: Collection<String>): List<MapCellDistrictEntity>
}

/** 지도 관계 레포지토리(SPEC-0030 확장). 등록·목록 조회는 생 SQL 어댑터가 하고, 삭제만 파생 쿼리다. */
interface MapRelationJpaRepository : JpaRepository<MapRelationEntity, Long> {

    /** 내 목록에서만 지운다. 상대 행은 남는다. 파생 삭제라 행마다 DELETE가 나간다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByOwnerIdAndCounterpartId(ownerId: Long, counterpartId: Long): Int

    /** 탈퇴 정리 — 양쪽 방향을 모두 지운다. 행마다 DELETE가 나간다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    fun deleteByOwnerIdOrCounterpartId(ownerId: Long, counterpartId: Long): Int
}
