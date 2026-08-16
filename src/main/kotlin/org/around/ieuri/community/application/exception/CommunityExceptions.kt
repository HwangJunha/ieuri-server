package org.around.ieuri.community.application.exception

/**
 * 사용자에게 그대로 보여줄 수 있는 예상 가능한 커뮤니티 실패. 인바운드 리졸버가 이 예외를
 * GraphQL 최상위 error가 아니라 payload의 `userErrors`로 변환한다.
 *
 * 인증 실패(UnauthorizedException)는 이 타입이 아니며 항상 최상위 errors[]로 전파된다 —
 * 클라이언트의 자동 토큰 갱신은 최상위 errors[]를 보는 링크 계층에서 일어나므로, 인증
 * 실패를 payload에 담으면 갱신이 트리거되지 않는다.
 */
abstract class CommunityUserException(val code: String, message: String) : RuntimeException(message)

class PostTitleRequiredException(message: String = "제목을 입력해 주세요.") :
    CommunityUserException("POST_TITLE_REQUIRED", message)

class PostTitleLengthInvalidException(message: String = "제목은 50자를 넘을 수 없습니다.") :
    CommunityUserException("POST_TITLE_LENGTH_INVALID", message)

class PostBodyRequiredException(message: String = "본문을 입력해 주세요.") :
    CommunityUserException("POST_BODY_REQUIRED", message)

class PostBodyLengthInvalidException(message: String = "본문은 500자를 넘을 수 없습니다.") :
    CommunityUserException("POST_BODY_LENGTH_INVALID", message)

class SpotNotFoundException(message: String = "해당 장소를 찾을 수 없습니다.") :
    CommunityUserException("SPOT_NOT_FOUND", message)

class InvalidTagException(message: String = "태그는 공백 없이 30자 이내여야 합니다.") :
    CommunityUserException("INVALID_TAG", message)

class TooManyTagsException(message: String = "태그는 최대 5개입니다.") :
    CommunityUserException("TOO_MANY_TAGS", message)

class TooManyPhotosException(message: String = "사진은 최대 5장입니다.") :
    CommunityUserException("TOO_MANY_PHOTOS", message)

/**
 * 미업로드 키와 타인·위조 키를 **같은 코드로** 다룬다. 어느 쪽인지 알려 주면 남의 오브젝트
 * 키가 존재하는지 캐내는 수단이 된다.
 */
class PhotoNotUploadedException(message: String = "업로드되지 않은 사진입니다.") :
    CommunityUserException("PHOTO_NOT_UPLOADED", message)

class UnsupportedContentTypeException(message: String = "지원하지 않는 이미지 형식입니다.") :
    CommunityUserException("UNSUPPORTED_CONTENT_TYPE", message)

/** 숨겨진 글은 수정할 수 없다 — 신고 기록이 대상 글을 가리키기 때문이다. */
class HiddenPostNotEditableException(message: String = "숨겨진 글은 수정할 수 없습니다.") :
    CommunityUserException("POST_NOT_EDITABLE", message)

class NotPostOwnerException(message: String = "본인의 글만 삭제할 수 있습니다.") :
    CommunityUserException("NOT_POST_OWNER", message)

class PostNotFoundException(message: String = "해당 글을 찾을 수 없습니다.") :
    CommunityUserException("POST_NOT_FOUND", message)

class CommentBodyRequiredException(message: String = "댓글을 입력해 주세요.") :
    CommunityUserException("COMMENT_BODY_REQUIRED", message)

class CommentBodyLengthInvalidException(message: String = "댓글은 500자를 넘을 수 없습니다.") :
    CommunityUserException("COMMENT_BODY_LENGTH_INVALID", message)

class NotCommentOwnerException(message: String = "본인의 댓글만 삭제할 수 있습니다.") :
    CommunityUserException("NOT_COMMENT_OWNER", message)

class CommentNotFoundException(message: String = "해당 댓글을 찾을 수 없습니다.") :
    CommunityUserException("COMMENT_NOT_FOUND", message)

class CannotBlockSelfException(message: String = "자기 자신은 차단할 수 없습니다.") :
    CommunityUserException("CANNOT_BLOCK_SELF", message)

class MemberNotFoundException(message: String = "해당 사용자를 찾을 수 없습니다.") :
    CommunityUserException("MEMBER_NOT_FOUND", message)

class AlreadyReportedException(message: String = "이미 신고한 대상입니다.") :
    CommunityUserException("ALREADY_REPORTED", message)

/** 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013) — 이 메시지에도 담기지 않는다. */
class ReportDetailRequiredException(message: String = "상세 사유를 입력해 주세요.") :
    CommunityUserException("REPORT_DETAIL_REQUIRED", message)

// ── DM(SPEC-0017·SPEC-0021) ──────────────────────────────────────────

/**
 * 확정되지 않았거나 이미 다른 글에 실린 사진 키. **두 경우를 같은 코드로 다룬다** —
 * 구분해 알려 주면 남의 오브젝트 키가 존재하는지 캐내는 수단이 된다. DM 사진이 쓴다.
 */
class PhotoNotConfirmedException(message: String = "업로드가 확정되지 않은 사진입니다.") :
    CommunityUserException("PHOTO_NOT_CONFIRMED", message)

/** 자기 자신과는 대화를 시작할 수 없다(SPEC-0021 AC2.3). */
class SelfConversationException(message: String = "자기 자신과는 대화할 수 없습니다.") :
    CommunityUserException("SELF_CONVERSATION", message)

/** 차단 관계에서는 대화 시작도 메시지 전송도 거부된다(AC11-3·11-4). */
class BlockedException(message: String = "차단된 상대와는 대화할 수 없습니다.") :
    CommunityUserException("BLOCKED", message)

/**
 * 없는 대화. **권한 없는 대화([NotConversationParticipantException])와 다른 코드다** —
 * 뭉뚱그리면 클라이언트가 "사라진 대화"와 "볼 수 없는 대화"를 구분하지 못한다(AC9-5·10-5).
 */
class ConversationNotFoundException(message: String = "해당 대화를 찾을 수 없습니다.") :
    CommunityUserException("CONVERSATION_NOT_FOUND", message)

/**
 * 뮤테이션에서 참여자가 아님. **조회·구독은 이 예외가 아니라 최상위 errors[]의 FORBIDDEN**이다 —
 * 조회는 볼 자격 자체가 없어 부분 성공이 성립하지 않는다(spec.md "권한 실패의 표면화 위치").
 */
class NotConversationParticipantException(message: String = "대화의 참여자가 아닙니다.") :
    CommunityUserException("NOT_CONVERSATION_PARTICIPANT", message)

/**
 * 신고 입력이 성립하지 않음. 네 경우를 덮는다 — 빈 목록, 여러 대화가 섞인 목록,
 * 자기 메시지 포함, 존재하지 않는 메시지 포함(AC15-3~15-5).
 */
class InvalidInputException(message: String = "잘못된 입력입니다.") :
    CommunityUserException("INVALID_INPUT", message)
