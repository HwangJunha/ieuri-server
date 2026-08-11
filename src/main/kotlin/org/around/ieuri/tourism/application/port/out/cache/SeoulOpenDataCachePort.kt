package org.around.ieuri.tourism.application.port.out.cache

import org.around.ieuri.tourism.application.port.out.client.SeoulOpenDataSnapshot
import java.time.Instant

/**
 * 서울 실시간 도시데이터 캐시 포트(SPEC-0014 R13·R14).
 *
 * **저장 시각을 값과 함께 담는다.** 키에 업스트림 타임스탬프를 넣지 않는 이유: 넣으면 이전
 * 시각의 키가 TTL까지 남아 메모리를 갉아먹고, "현재 값"을 찾으려면 키를 스캔해야 한다.
 * 값 안에 두면 조회 즉시 신선도를 판정할 수 있다.
 *
 * 구현체는 저장소 장애를 삼킨다(get→null, put→무시) — 캐시는 정합성 소스가 아니다.
 * auth의 refresh 토큰 저장소가 같은 인스턴스를 쓰지만 fail-closed(POL-008)로 **따로**
 * 동작하므로, 여기서 예외를 삼켜도 그쪽에 영향이 없다.
 */
interface SeoulOpenDataCachePort {
    /** 구역 1곳의 캐시된 값. 미스면 null. */
    fun get(areaCode: String): CachedSeoulOpenData?

    /** 구역 1곳을 [storedAt] 기준으로 저장한다. */
    fun put(areaCode: String, snapshot: SeoulOpenDataSnapshot, storedAt: Instant)
}

/**
 * 캐시된 값과 그 저장 시각.
 *
 * [storedAt]이 조회 시각이 아니라 **저장 시각**인 것이 요점이다 — 데이터가 약 30분 지연되어
 * 도착하므로(spec §4.2) "5분 경계까지 TTL"로 잡으면 같은 데이터를 6번 재호출한다.
 * 저장 시각과 최소 재호출 간격을 비교해야 호출량이 준다.
 */
data class CachedSeoulOpenData(
    val snapshot: SeoulOpenDataSnapshot,
    val storedAt: Instant,
)
