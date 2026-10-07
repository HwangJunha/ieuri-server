package org.around.ieuri.tourism.application.catalog.provided.dto

/** 배정 결과. [joined]는 기존 카탈로그에 붙은 행, [catalogsCreated]는 새 카탈로그를 만든 행이다. */
data class CatalogAssignmentResult(
    val joined: Int,
    val catalogsCreated: Int,
    /**
     * 표시할 제목이 없어 배정하지 않은 행. 미배정으로 남아 다음 실행에 다시 조회된다.
     * - 언어 소진 판정은 [assigned]가 아니라 [processed]로 한다.
     */
    val skipped: Int = 0,
    /** 이번 회차가 읽은 마지막 spot id. 다음 회차의 키셋 커서다. */
    val lastSpotId: Long = 0L,
) {
    val assigned: Int get() = joined + catalogsCreated

    /** 이 회차가 무언가 읽었는가. 언어 소진 판정의 기준이다. */
    val processed: Int get() = assigned + skipped
}

/** 분할 결과. [catalogsSplit]은 갈라진 옛 카탈로그 수, [catalogsCreated]는 새로 만든 카탈로그 수다. */
data class CatalogSplitResult(
    val catalogsSplit: Int,
    val catalogsCreated: Int,
    val spotsMoved: Int,
)

/** 시군구 귀속 결과. [unassigned]는 바다·경계 밖 좌표라 0이 되지 않는다 — 갑자기 커지면 경계·좌표 적재를 의심한다. */
data class DistrictAssignmentResult(
    val assigned: Int,
    val unassigned: Int,
)
