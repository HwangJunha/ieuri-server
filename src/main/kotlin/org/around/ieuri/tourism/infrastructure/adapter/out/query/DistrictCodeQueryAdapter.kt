package org.around.ieuri.tourism.infrastructure.adapter.out.query

import org.around.ieuri.tourism.application.port.out.persistence.DistrictCodeQueryPort
import org.around.ieuri.tourism.application.port.out.persistence.DistrictRegion
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.AreaJpaRepository
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository.DistrictJpaRepository
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 등록된 행정구역 코드 집합 조회(SPEC-0015 R9).
 *
 * **공간 컬럼을 읽지 않아 생 SQL이 필요 없다.** `district.boundary`가 `geography`인 것은
 * 사실이지만 그 제약은 **그 컬럼을 만지는 질의**에만 걸린다 — 코드와 이름만 읽는 여기는
 * 엔티티로 충분하다(`DistrictEntity`가 geography 3개를 매핑에서 뺀 이유이기도 하다).
 *
 * 배치가 실행당 한 번만 부른다 — 252행이라 전량을 메모리에 올려 대조하는 편이 행마다 EXISTS를
 * 던지는 것보다 훨씬 싸다.
 */
@Component
class DistrictCodeQueryAdapter(
    private val districtRepository: DistrictJpaRepository,
    private val areaRepository: AreaJpaRepository,
) : DistrictCodeQueryPort {

    @Transactional(readOnly = true)
    override fun findAllCodes(): Set<String> =
        districtRepository.findAll().mapTo(mutableSetOf()) { it.code }

    /** 코드 오름차순 — 배치가 인덱스로 진행 위치를 기억하므로 순서가 안정적이어야 한다. */
    @Transactional(readOnly = true)
    override fun findAllRegions(): List<DistrictRegion> =
        districtRepository.findAll(Sort.by("code"))
            .map { DistrictRegion(code = it.code, provinceCode = it.areaCd) }

    /** 17행. `area`가 원본의 상위 계층이라 DISTINCT 없이 그대로 읽는다(V74). */
    @Transactional(readOnly = true)
    override fun findAllProvinceCodes(): List<String> =
        areaRepository.findAll(Sort.by("areaCd")).map { it.areaCd }
}
