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
