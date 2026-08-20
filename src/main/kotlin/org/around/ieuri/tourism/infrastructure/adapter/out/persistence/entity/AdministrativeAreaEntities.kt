package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.around.ieuri.common.persistence.DbSchema
import java.io.Serializable

/**
 * 시도 JPA 엔티티(KTO 원본 `areaCd`, V74). 17행.
 *
 * PK가 대리키가 아니라 코드다 — 행정표준코드는 정부가 부여한 안정 식별자라 대리키를 얹으면
 * 의미 없는 층이 하나 늘 뿐이다([DistrictEntity]도 같다).
 */
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
 * 시군구 JPA 엔티티(KTO 원본 `sigunguCd`, V44·V74). 252행.
 *
 * **`boundary`·`center`·`representative_location`은 매핑하지 않는다** — `geography`라 JPA가
 * 표현할 타입이 없고, 매핑하면 `ddl-auto: validate`가 기동 시 실패한다. 그 컬럼을 만지는
 * 질의는 생 SQL 어댑터에만 있다(`SpotCatalogEntity`가 `location`을 다루는 방식과 같다).
 *
 * 그래서 이 엔티티로는 **코드·이름 조회만** 한다. 공간 질의는 여전히 생 SQL이다.
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
 * 칸이 묶는 시군구(V74). 한 칸이 1~25개를 묶는다.
 *
 * **자연키가 곧 PK다.** 두 코드의 조합 외에 이 표에 담을 것이 없어 대리키를 얹을 이유가 없다 —
 * `member_district_visit`이 대리키를 쓴 것과 다른 판단인데, 그쪽은 멱등 등록이 자연키 UNIQUE를
 * 필요로 해 PK와 별개의 제약이 생겼고 이쪽은 그렇지 않다.
 */
@Entity
@Table(name = "map_cell_district", schema = DbSchema.TOUR)
@IdClass(MapCellDistrictId::class)
class MapCellDistrictEntity(
    @Id
    @Column(name = "cell_code", nullable = false, length = 20)
    var cellCode: String,

    @Id
    @Column(name = "district_code", nullable = false, length = 5)
    var districtCode: String,
)

/** [MapCellDistrictEntity]의 복합 식별자. JPA가 요구하는 형태이며 값은 엔티티와 같다. */
data class MapCellDistrictId(
    var cellCode: String = "",
    var districtCode: String = "",
) : Serializable
