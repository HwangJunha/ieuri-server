package org.around.ieuri.community.infrastructure.adapter.out.query.mapper

import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity

/*
 * 메시지 엔티티 → 읽기 모델 변환(SPEC-0026, POL-033).
 *
 * **암복호화를 하지 않는다** — 복호화한 평문을 파라미터로 받아 순수 함수로 남는다.
 * 어댑터 안에 두면 cipher 없이는 호출할 수 없어 필드별 검증이 불가능해진다.
 */

/**
 * 메시지의 인용 스냅샷(R14). 인용 행이 있으면 반드시 성립한다 — 컬럼이 NOT NULL이라
 * 부분적으로 채워진 상태가 없다.
 *
 * **자리 배치가 종류마다 다르다** — 장소 답변은 title=장소명, primary=질문, secondary=답변,
 * context=카탈로그, parent=질문 id다. 필드가 여럿이고 같은 타입이 섞여 뒤바뀌어도 컴파일이
 * 통과하므로 이 변환은 필드별로 검증한다.
 */
fun MessageQuoteEntity.toSpotQaOriginView(
    decryptedPrimaryExcerpt: String,
    decryptedSecondaryExcerpt: String,
): ConversationSpotQaOriginView = ConversationSpotQaOriginView(
    catalogId = requireNotNull(contextId) { "장소 답변 인용에는 카탈로그 id가 있어야 한다." },
    spotTitle = titleSnapshot,
    questionId = requireNotNull(parentId) { "장소 답변 인용에는 질문 id가 있어야 한다." },
    questionBodySnapshot = decryptedPrimaryExcerpt,
    answerId = targetId,
    answerBodySnapshot = decryptedSecondaryExcerpt,
)
