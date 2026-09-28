package org.around.ieuri.community.infrastructure.adapter.out.query.mapper

import org.around.ieuri.community.application.query.ConversationSpotQaOriginView
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.MessageQuoteEntity

/**
 * 메시지의 인용 스냅샷을 읽기 모델로(SPEC-0026 R14). 복호화된 발췌를 받는다.
 * - 장소 답변 배치: title=장소명, primary=질문, secondary=답변, context=카탈로그, parent=질문 id.
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
