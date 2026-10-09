package org.around.ieuri.tourism.application.region.required

import org.around.ieuri.tourism.domain.region.ConcentrationRate
import org.around.ieuri.tourism.domain.region.DemandIntensity
import org.around.ieuri.tourism.domain.region.District
import org.around.ieuri.tourism.domain.region.RepresentativeLocation
import org.around.ieuri.tourism.domain.region.VisitorCount

/** 시군구 애그리게이트 저장소 — 대표 위치와 지표(자식)를 쓴다. 지표는 자연키 upsert이며 값이 바뀐 행만 `updated_at`을 올린다. */
interface DistrictRepository {
    /** 등록된 시군구 전체. 배치 실행 단위마다 한 번 읽는다. */
    fun findAll(): List<District>

    /** 고른 원본의 좌표를 대표 위치로 둔다. 시각은 항상 갱신한다. */
    fun saveRepresentative(districtCode: String, choice: RepresentativeLocation.Choice)

    /** 대표 위치를 비운다. 이미 비어 있는 행은 건드리지 않는다. */
    fun clearRepresentative(districtCode: String)

    fun upsertVisitorCounts(counts: List<VisitorCount>): Int

    fun upsertConcentrationRates(rates: List<ConcentrationRate>): Int

    fun upsertStayIntensities(intensities: List<DemandIntensity>): Int

    fun upsertSpendingIntensities(intensities: List<DemandIntensity>): Int
}
