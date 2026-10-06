package org.around.ieuri.tourism.application.shared.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/**
 * 공유 토큰이 없거나 폐기됐다(SPEC-0030 R14).
 * - 둘을 구분해 응답하지 않는다 — 구분하면 공유 이력이 새어 나간다.
 */
class ShareLinkNotFoundException(message: String = "더 이상 볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_NOT_FOUND", Classification.NOT_FOUND, message)
