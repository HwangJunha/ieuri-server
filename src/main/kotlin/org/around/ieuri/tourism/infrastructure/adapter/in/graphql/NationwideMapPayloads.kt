package org.around.ieuri.tourism.infrastructure.adapter.`in`.graphql

import org.around.ieuri.common.graphql.generated.types.UserError
import org.around.ieuri.tourism.application.query.MapCellView

/**
 * 방문 뮤테이션의 응답 타입.
 *
 * **코드젠이 만든 `RecordVisitsPayload`를 쓰지 않는다.** 생성된 `MapCell`은 non-null 필드를
 * 생성자 필수 인자로 요구해서 `spots`·`representativeImage` 같은 지연 필드를 비워 둘 수 없다.
 * 그것을 채우면 클라이언트가 고르지도 않은 spot 목록을 뮤테이션이 매번 조회하게 된다.
 *
 * 대신 읽기 DTO([MapCellView])를 그대로 담아 `MapCell` 필드 리졸버가 이어받게 한다 —
 * `spotDetail`이 읽기 DTO를 반환하는 것과 같은 이유다.
 *
 * **이름이 `...Payload`가 아니라 `...Response`인 것은 의도다.** 스키마 이름과 같게 두면
 * 코드젠이 만든 동명 클래스와 import 한 줄로만 구별되어, 나중에 스키마에 필드를 더했을 때
 * 이쪽이 낡은 채로 남아도 컴파일이 통과한다(실패는 런타임 FieldUndefined로만 드러난다).
 * DGS는 반환 객체의 프로퍼티 이름으로 필드를 찾으므로 클래스 이름은 달라도 된다.
 */
data class RecordVisitsResponse(
    val success: Boolean,
    val userErrors: List<UserError>,
    val updatedCells: List<MapCellView>,
)

/** 방문 해제 응답. 위와 같은 이유로 생성 타입이 아니다. */
data class RemoveVisitResponse(
    val success: Boolean,
    val userErrors: List<UserError>,
    val updatedCell: MapCellView?,
)
