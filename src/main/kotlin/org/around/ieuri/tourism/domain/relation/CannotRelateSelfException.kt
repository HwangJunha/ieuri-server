package org.around.ieuri.tourism.domain.relation

import org.around.ieuri.tourism.domain.shared.TourismQueryException
import org.around.ieuri.tourism.domain.shared.TourismQueryException.Classification

/** 자기 자신과 관계를 맺으려 했다(SPEC-0030). DB CHECK보다 먼저 도메인(`MapRelation`)이 막는다. */
class CannotRelateSelfException(message: String = "자기 자신과는 관계를 맺을 수 없습니다.") : TourismQueryException("CANNOT_RELATE_SELF", Classification.BAD_REQUEST, message)
