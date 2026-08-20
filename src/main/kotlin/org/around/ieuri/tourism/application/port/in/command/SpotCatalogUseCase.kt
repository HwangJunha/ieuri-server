package org.around.ieuri.tourism.application.port.`in`.command

import org.around.ieuri.tourism.domain.vo.DomainLocale

/**
 * 카탈로그 배정 유스케이스(SPEC-0019). 배치가 호출한다.
 *
 * **번역 채움은 없다**(SPEC-0029 R-6). 언어 슬롯은 원본이 있는 언어에만 생기며, 비어 있는
 * 언어는 비어 있는 채로 조회에서 빠진다.
 */
interface SpotCatalogUseCase {

    /**
     * 미배정 spot(`catalog_id`가 null인 행)을 카탈로그에 배정한다.
     *
     * **전량 재판정하지 않는다**(설계 문서 §3.3). 배정된 행은 다시 보지 않으므로 미배정
     * 조건 하나가 곧 증분 처리다 — 적재가 새 행을 넣으면 그 행만 자연히 대상이 된다.
     *
     * KO 행이 뒤늦게 생겨 기존 외국어 카탈로그와 같은 장소가 되더라도 **병합하지 않는다** —
     * 두 카탈로그가 공존하며 각자의 좋아요를 유지한다. 병합은 좋아요를 딸려 이동시키므로
     * 잘못되면 사용자 데이터가 엉킨다.
     */
    fun assignCatalogs(language: DomainLocale, afterSpotId: Long = 0L): CatalogAssignmentResult

    /**
     * 원본이 사라진 카탈로그를 조회에서 감추고, 돌아온 것은 다시 보이게 한다.
     * 상태가 바뀐 행 수를 돌려준다.
     *
     * **배정 뒤에 부른다** — 배정이 새 spot을 붙이고 나서야 "정말 원본이 없는" 카탈로그가
     * 확정된다. 앞에 두면 방금 들어온 장소를 잠시 감춘다.
     */
    fun refreshCatalogVisibility(): Int
}

/**
 * 배정 결과.
 *
 * [joined]는 기존 카탈로그에 붙은 행, [catalogsCreated]는 새 카탈로그를 만든 행이다.
 * 비한국어에서 [catalogsCreated]가 크면 그 언어에만 있는 장소가 많다는 뜻이며, 정규화 규칙이
 * 무너졌을 때도 같은 신호가 나오므로 추이를 본다.
 */
data class CatalogAssignmentResult(
    val joined: Int,
    val catalogsCreated: Int,
    /**
     * 표시할 제목이 없어 배정하지 않은 행. **미배정으로 남으므로 다음 회차에 다시 조회된다.**
     *
     * 언어 전환 판정이 이 값을 봐야 한다 — [assigned]만 보면 한 청크가 전부 skip일 때
     * "소진됐다"고 오판해 그 언어의 나머지를 통째로 건너뛴다.
     */
    val skipped: Int = 0,
    /** 이번 회차가 읽은 마지막 spot id. 다음 회차의 키셋 커서다. */
    val lastSpotId: Long = 0L,
) {
    val assigned: Int get() = joined + catalogsCreated

    /** 이 회차가 무언가 읽었는가. 언어 소진 판정의 기준이다. */
    val processed: Int get() = assigned + skipped
}
