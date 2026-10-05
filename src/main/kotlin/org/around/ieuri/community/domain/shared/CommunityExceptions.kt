package org.around.ieuri.community.domain.shared

/**
 * 사용자에게 보여줄 수 있는 커뮤니티 실패. 어댑터가 payload `userErrors`의 code로 옮긴다.
 * - 인증 실패는 이 타입이 아니며 항상 최상위 errors[]로 전파된다(토큰 자동 갱신 트리거).
 */
abstract class CommunityUserException(val code: String, message: String) : RuntimeException(message)

/** 미업로드 키와 타인·위조 키를 같은 코드로 다룬다(키 존재 여부를 노출하지 않는다). */
class PhotoNotUploadedException(message: String = "업로드되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_UPLOADED", message)

class TooManyPhotosException(message: String = "사진은 최대 5장입니다.") : CommunityUserException("TOO_MANY_PHOTOS", message)

/** 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013) — 이 메시지에도 담기지 않는다. */
class ReportDetailRequiredException(message: String = "상세 사유를 입력해 주세요.") : CommunityUserException("REPORT_DETAIL_REQUIRED", message)

class AlreadyReportedException(message: String = "이미 신고한 대상입니다.") : CommunityUserException("ALREADY_REPORTED", message)

/** 입력 형식·규칙 위반. 메시지가 곧 사용자에게 보이는 사유다. */
class InvalidInputException(message: String = "잘못된 입력입니다.") : CommunityUserException("INVALID_INPUT", message)
