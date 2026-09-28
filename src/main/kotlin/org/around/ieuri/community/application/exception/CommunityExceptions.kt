package org.around.ieuri.community.application.exception

/**
 * 사용자에게 보여줄 수 있는 커뮤니티 실패. 리졸버가 payload의 `userErrors`로 변환한다.
 * - 인증 실패는 이 타입이 아니며 항상 최상위 errors[]로 전파된다(토큰 자동 갱신 트리거).
 */
abstract class CommunityUserException(val code: String, message: String) : RuntimeException(message)

class PostTitleRequiredException(message: String = "제목을 입력해 주세요.") : CommunityUserException("POST_TITLE_REQUIRED", message)

class PostTitleLengthInvalidException(message: String = "제목은 50자를 넘을 수 없습니다.") : CommunityUserException("POST_TITLE_LENGTH_INVALID", message)

class PostBodyRequiredException(message: String = "본문을 입력해 주세요.") : CommunityUserException("POST_BODY_REQUIRED", message)

class PostBodyLengthInvalidException(message: String = "본문은 500자를 넘을 수 없습니다.") : CommunityUserException("POST_BODY_LENGTH_INVALID", message)

/** 카탈로그 자체가 없다. 어떤 언어로 물어도 없는 id다. */
class SpotNotFoundException(message: String = "해당 장소를 찾을 수 없습니다.") : CommunityUserException("SPOT_NOT_FOUND", message)

/**
 * 카탈로그는 있으나 활성 언어에 원본이 없다(SPEC-0029). [SpotNotFoundException]과 코드를 구분한다.
 * - 뮤테이션에서는 userErrors로, `spotQuestions` 조회에서는 최상위 errors[]로 나간다.
 */
class SpotNotFoundInLocaleException(message: String = "이 언어에서는 볼 수 없는 장소입니다.") : CommunityUserException("SPOT_NOT_FOUND_IN_LOCALE", message)

class InvalidTagException(message: String = "태그는 공백 없이 30자 이내여야 합니다.") : CommunityUserException("INVALID_TAG", message)

class TooManyTagsException(message: String = "태그는 최대 5개입니다.") : CommunityUserException("TOO_MANY_TAGS", message)

class TooManyPhotosException(message: String = "사진은 최대 5장입니다.") : CommunityUserException("TOO_MANY_PHOTOS", message)

/** 미업로드 키와 타인·위조 키를 같은 코드로 다룬다(키 존재 여부를 노출하지 않는다). */
class PhotoNotUploadedException(message: String = "업로드되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_UPLOADED", message)

class UnsupportedContentTypeException(message: String = "지원하지 않는 이미지 형식입니다.") : CommunityUserException("UNSUPPORTED_CONTENT_TYPE", message)

/** 숨겨진 글은 수정할 수 없다. */
class HiddenPostNotEditableException(message: String = "숨겨진 글은 수정할 수 없습니다.") : CommunityUserException("POST_NOT_EDITABLE", message)

class NotPostOwnerException(message: String = "본인의 글만 삭제할 수 있습니다.") : CommunityUserException("NOT_POST_OWNER", message)

class PostNotFoundException(message: String = "해당 글을 찾을 수 없습니다.") : CommunityUserException("POST_NOT_FOUND", message)

class CommentBodyRequiredException(message: String = "댓글을 입력해 주세요.") : CommunityUserException("COMMENT_BODY_REQUIRED", message)

class CommentBodyLengthInvalidException(message: String = "댓글은 500자를 넘을 수 없습니다.") : CommunityUserException("COMMENT_BODY_LENGTH_INVALID", message)

class NotCommentOwnerException(message: String = "본인의 댓글만 삭제할 수 있습니다.") : CommunityUserException("NOT_COMMENT_OWNER", message)

class CommentNotFoundException(message: String = "해당 댓글을 찾을 수 없습니다.") : CommunityUserException("COMMENT_NOT_FOUND", message)

class CannotBlockSelfException(message: String = "자기 자신은 차단할 수 없습니다.") : CommunityUserException("CANNOT_BLOCK_SELF", message)

class MemberNotFoundException(message: String = "해당 사용자를 찾을 수 없습니다.") : CommunityUserException("MEMBER_NOT_FOUND", message)

class AlreadyReportedException(message: String = "이미 신고한 대상입니다.") : CommunityUserException("ALREADY_REPORTED", message)

/** 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013) — 이 메시지에도 담기지 않는다. */
class ReportDetailRequiredException(message: String = "상세 사유를 입력해 주세요.") : CommunityUserException("REPORT_DETAIL_REQUIRED", message)

// ── DM(SPEC-0017·SPEC-0021) ──────────────────────────────────────────

/** 확정되지 않았거나 이미 다른 글에 실린 DM 사진 키. 두 경우를 같은 코드로 다룬다. */
class PhotoNotConfirmedException(message: String = "업로드가 확정되지 않은 사진입니다.") : CommunityUserException("PHOTO_NOT_CONFIRMED", message)

/** 자기 자신과는 대화를 시작할 수 없다(SPEC-0021 AC2.3). */
class SelfConversationException(message: String = "자기 자신과는 대화할 수 없습니다.") : CommunityUserException("SELF_CONVERSATION", message)

/** 차단 관계에서는 대화 시작도 메시지 전송도 거부된다(AC11-3·11-4). */
class BlockedException(message: String = "차단된 상대와는 대화할 수 없습니다.") : CommunityUserException("BLOCKED", message)

/** 없는 대화. 권한 없는 대화([NotConversationParticipantException])와 다른 코드다. */
class ConversationNotFoundException(message: String = "해당 대화를 찾을 수 없습니다.") : CommunityUserException("CONVERSATION_NOT_FOUND", message)

/** 뮤테이션에서 참여자가 아님. 조회·구독은 이 예외가 아니라 최상위 errors[]의 FORBIDDEN이다. */
class NotConversationParticipantException(message: String = "대화의 참여자가 아닙니다.") : CommunityUserException("NOT_CONVERSATION_PARTICIPANT", message)

/** 신고 입력 불성립: 빈 목록, 여러 대화 혼합, 자기 메시지 포함, 없는 메시지 포함. */
class InvalidInputException(message: String = "잘못된 입력입니다.") : CommunityUserException("INVALID_INPUT", message)

// ── 장소 질문/답변(SPEC-0026) ────────────────────────────────────────

class QuestionNotFoundException(message: String = "해당 질문을 찾을 수 없습니다.") : CommunityUserException("QUESTION_NOT_FOUND", message)

class AnswerNotFoundException(message: String = "해당 답변을 찾을 수 없습니다.") : CommunityUserException("ANSWER_NOT_FOUND", message)

class ReplyNotFoundException(message: String = "해당 답글을 찾을 수 없습니다.") : CommunityUserException("REPLY_NOT_FOUND", message)

/** 답글에 답글을 달려는 입력 오류(SPEC-0026 R7). */
class ReplyToReplyNotAllowedException(message: String = "답글에는 답글을 달 수 없습니다.") : CommunityUserException("REPLY_TO_REPLY_NOT_ALLOWED", message)

class NotQuestionOwnerException(message: String = "본인의 질문만 처리할 수 있습니다.") : CommunityUserException("NOT_QUESTION_OWNER", message)

class NotAnswerOwnerException(message: String = "본인의 답변만 삭제할 수 있습니다.") : CommunityUserException("NOT_ANSWER_OWNER", message)

class NotReplyOwnerException(message: String = "본인의 답글만 삭제할 수 있습니다.") : CommunityUserException("NOT_REPLY_OWNER", message)
