package org.around.ieuri.tourism.application.port.out.persistence

import org.around.ieuri.tourism.application.command.SyncSpotCommand
import org.around.ieuri.tourism.domain.vo.DomainLocale
import java.time.Instant

/**
 * 관광지 목록 적재 아웃바운드 포트(SPEC-0004 R-1·R-4). **쓰기 전용** — 조회는 읽기 포트가 따로 담당한다
 * (CQRS: 읽기를 애그리거트·쓰기 포트로 우회시키지 않는다).
 *
 * application은 이 인터페이스만 보고 `SpotJpaAdapter`·`SpotEntity`를 알지 않는다(AC-52).
 */
interface SpotStorePort {
    /**
     * 한 페이지 분량을 upsert한다. 자연키 (contentId, language) 충돌 시 전체 컬럼을 덮어쓴다.
     *
     * **해시 비교로 변경을 감지하지 않는다**(설계 문서 §2.4). 하루 1회 배치에서 쓰기 I/O를 아끼는 이득은
     * 작은 반면, 해시에 `cpyrhtDivCd`를 빠뜨리면 낡은 `UNSPECIFIED` 저작권 분류가 무기한 남는 위험이
     * 명확하기 때문이다(런타임 계약 위험 5번). 전량 덮어쓰기는 AC-10(행 수 불변)·AC-11(연속 실행 동일
     * 결과)을 자명하게 만족시킨다.
     *
     * [syncedAt]은 이번 실행 시각으로, 건드린 행의 `last_synced_at`에 기록되어 정리 단계(R-5)의 기준이 된다.
     * @return 실제로 upsert된 행 수(AC-18의 언어별 처리 건수 집계용).
     */
    fun upsertAll(spots: List<SyncSpotCommand>, syncedAt: Instant): Int

    /**
     * [language]에 적재된 전체 행 수. 정리 단계의 급감 감지 게이트와 실행 이력에 쓴다.
     */
    fun countByLanguage(language: DomainLocale): Long

    /**
     * [language]에서 [survivingContentIds]에 **없는** 행을 삭제한다(R-5). 이번 실행이 수집한
     * contentId 집합이 곧 "업스트림에 현존하는 것"이므로, 그 여집합이 사라진 항목이다.
     *
     * **시각(`last_synced_at`) 기준이 아니라 집합 기준이다.** 시각 기준은 "이번 실행이 건드렸나"를
     * 보는데, 부분 실패나 페이지 경계 누락과 구분되지 않는다. 집합 기준은 "무엇을 봤나"를 직접
     * 다루므로 게이트(전량 성공·0건·급감)를 명확한 조건으로 걸 수 있다.
     *
     * 데이터가 언어당 최대 6만 건 규모라 집합을 메모리에 들고 한 번에 비교하는 것이 현실적이다
     * (실측: KO 56,897건 ≈ 6MB). 언어별로 독립 수행하므로 한 언어가 끝나면 집합을 버린다.
     *
     * **호출자가 게이트를 통과시킨 뒤에만 부른다** — 이 메서드는 안전 판정을 하지 않는다.
     * @return 삭제된 행 수.
     */
    fun deleteMissing(language: DomainLocale, survivingContentIds: Set<String>): Int
}
