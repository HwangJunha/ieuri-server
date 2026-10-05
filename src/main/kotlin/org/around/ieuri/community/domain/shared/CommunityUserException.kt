package org.around.ieuri.community.domain.shared

/**
 * 사용자에게 보여줄 수 있는 커뮤니티 실패. 어댑터가 payload `userErrors`의 code로 옮긴다.
 * - 인증 실패는 이 타입이 아니며 항상 최상위 errors[]로 전파된다(토큰 자동 갱신 트리거).
 */
abstract class CommunityUserException(val code: String, message: String) : RuntimeException(message)
