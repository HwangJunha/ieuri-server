package org.around.ieuri.auth.application.member.provided.exception
import org.around.ieuri.auth.domain.shared.UserFacingException

/** 본인의 마지막 관리 역할을 스스로 회수하려 할 때(자기 강등 방지). */
class SelfDemotionException(message: String) : UserFacingException("SELF_DEMOTION", message)
