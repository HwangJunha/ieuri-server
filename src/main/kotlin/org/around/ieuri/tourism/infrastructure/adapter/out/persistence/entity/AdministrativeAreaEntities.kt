package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import java.io.Serializable

/** 시도 JPA 엔티티(KTO 원본 `areaCd`, V74). PK는 행정표준코드다. */
@Entity
@Table(name = "area", schema = DbSchema.TOUR)
class AreaEntity(
    @Id
    @Column(name = "area_cd", nullable = false, length = 2)
    var areaCd: String,

    @Column(name = "name_ko", nullable = false, length = 128)
    var nameKo: String,

    @Column(name = "name_en", nullable = false, length = 128)
    var nameEn: String,

    /** 원본에 번역이 없을 수 있다. 빈 칸을 EN으로 채우지 않는다 — 폴백은 조회 시점의 일이다. */
    @Column(name = "name_ja", length = 128)
    var nameJa: String? = null,

    @Column(name = "name_zh", length = 128)
    var nameZh: String? = null,
)

/**
 * 시군구 JPA 엔티티(KTO 원본 `sigunguCd`, V44·V74). 코드·이름 조회만 한다.
 * - `boundary`·`center`·`representative_location`(geography)은 매핑하지 않는다 — 공간 질의는 생 SQL 어댑터에 있다.
 */
@Entity
@Table(name = "district", schema = DbSchema.TOUR)
class DistrictEntity(
    @Id
    @Column(name = "code", nullable = false, length = 5)
    var code: String,

    @Column(name = "area_cd", nullable = false, length = 2)
    var areaCd: String,

    @Column(name = "name_ko", nullable = false, length = 128)
    var nameKo: String,

    @Column(name = "name_en", nullable = false, length = 128)
    var nameEn: String,

    @Column(name = "name_ja", length = 128)
    var nameJa: String? = null,

    @Column(name = "name_zh", length = 128)
    var nameZh: String? = null,
)

/**
 * 전국 지도의 표시 칸(V74). 공식 코드가 없는 표시 단위라 대리키다.
 * - 상위 시도 컬럼이 없다 — `district.area_cd`로 얻는 파생값이다.
 */
@Entity
@Table(name = "map_cell", schema = DbSchema.TOUR)
class MapCellEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(name = "name_ko", nullable = false, length = 128)
    var nameKo: String,

    @Column(name = "name_en", nullable = false, length = 128)
    var nameEn: String,

    @Column(name = "name_ja", length = 128)
    var nameJa: String? = null,

    @Column(name = "name_zh", length = 128)
    var nameZh: String? = null,
)

/** 칸이 묶는 시군구(V74). 한 칸이 1~25개를 묶으며 자연키가 곧 PK다. */
@Entity
@Table(name = "map_cell_district", schema = DbSchema.TOUR)
@IdClass(MapCellDistrictId::class)
class MapCellDistrictEntity(
    @Id
    @Column(name = "cell_id", nullable = false)
    var cellId: Long,

    @Id
    @Column(name = "district_code", nullable = false, length = 5)
    var districtCode: String,
)

/** [MapCellDistrictEntity]의 복합 식별자. */
data class MapCellDistrictId(
    var cellId: Long = 0,
    var districtCode: String = "",
) : Serializable
