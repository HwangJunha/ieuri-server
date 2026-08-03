package org.around.ieuri.tourism.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.tourism.domain.vo.DomainLocale
import org.around.ieuri.tourism.infrastructure.adapter.out.persistence.entity.SpotEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

/**
 * `spot` Spring Data 레포지토리. **자연키로 surrogate PK를 얻는 것이 유일한 용도다**(V19).
 *
 * 자식 테이블이 `spot_id`로 FK를 걸면서, 자연키(`contentId`)만 가진 쓰기 경로가 부모 id를 알아야
 * 한다. 적재·조회 본체는 여전히 네이티브 SQL([SpotJpaAdapter]·QueryDSL)이 담당하며 이 레포지토리를
 * 거치지 않는다 — 엔티티 전체를 로드하지 않고 id 하나만 뽑는 것이 목적이다.
 */
interface SpotJpaRepository : JpaRepository<SpotEntity, Long> {
    /**
     * 자연키에 대응하는 `spot.id`. 행이 없으면 null이며, 호출자는 그것을 **부모 없음**으로 처리해
     * 적재를 건너뛴다. 예외로 올리지 않는다 — 부모 부재를 사용자 오류로 표면화할지는 유스케이스가
     * 정할 문제이고(`ReactToSpotService`의 AC-14b 주석 참조), 어댑터가 앞당겨 결정하면 기존에
     * 성공하던 요청의 관찰 가능한 계약이 조용히 바뀐다.
     *
     * 엔티티가 아니라 스칼라를 돌려준다. 영속성 컨텍스트에 불필요한 관리 객체를 남기지 않는다.
     */
    @Query("SELECT s.id FROM SpotEntity s WHERE s.contentId = :contentId AND s.language = :language")
    fun findIdByContentIdAndLanguage(
        @Param("contentId") contentId: String,
        @Param("language") language: DomainLocale,
    ): Long?
}
