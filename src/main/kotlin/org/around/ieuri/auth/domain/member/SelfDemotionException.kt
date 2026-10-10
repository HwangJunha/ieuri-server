package org.around.ieuri.auth.domain.member

import org.around.ieuri.auth.domain.shared.UserFacingException

/** 본인의 마지막 관리 역할을 스스로 회수하려 할 때. */
class SelfDemotionException(message: String = "마지막 관리 역할은 스스로 회수할 수 없습니다.") : UserFacingException("SELF_DEMOTION", message)
