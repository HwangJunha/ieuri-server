package org.around.ieuri.tourism.application.footprint.provided.exception

import org.around.ieuri.tourism.domain.shared.SpotQueryException
import org.around.ieuri.tourism.domain.shared.SpotQueryException.Classification

/** 차단 관계라 공유 지도를 볼 수 없다(SPEC-0030 R16, POL-041). */
class ShareLinkBlockedException(message: String = "볼 수 없는 지도입니다.") : SpotQueryException("SHARE_LINK_BLOCKED", Classification.FAILED_PRECONDITION, message)
