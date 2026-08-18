package org.around.ieuri.community.infrastructure.adapter.out.query.mapper

import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.ConversationEntity

/**
 * 대화 엔티티 → 읽기 모델 변환(SPEC-0026, POL-033).
 *
 * **암복호화를 하지 않는다** — 복호화한 평문을 파라미터로 받아 순수 함수로 남는다.
 * 어댑터 안에 두면 cipher 없이는 호출할 수 없어 필드별 검증이 불가능해진다.
 */

/**
 * DM 출처 스냅샷(R14). **여섯 값이 전부 있어야 성립한다** — DB의 CHECK가 그 상태를 강제하므로
 * 하나라도 비면 출처 없는 일반 DM으로 읽는다.
 *
 * 필드가 여섯이고 그중 셋이 `Long`, 셋이 `String`이라 뒤바뀌어도 컴파일이 통과한다 —
 * 그래서 이 변환은 필드별로 검증한다.
 */
fun ConversationEntity.toSpotQaOriginView(
    decryptedQuestionExcerpt: String?,
    decryptedAnswerExcerpt: String?,
): ConversationSpotQaOriginView? = ConversationSpotQaOriginView(
    catalogId = spotQaCatalogId ?: return null,
    spotTitle = spotQaSpotTitle ?: return null,
    questionId = spotQaQuestionId ?: return null,
    questionBodySnapshot = decryptedQuestionExcerpt ?: return null,
    answerId = spotQaAnswerId ?: return null,
    answerBodySnapshot = decryptedAnswerExcerpt ?: return null,
)
