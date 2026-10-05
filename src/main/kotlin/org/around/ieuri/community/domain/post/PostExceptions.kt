package org.around.ieuri.community.domain.post

import org.around.ieuri.community.domain.shared.CommunityUserException

class PostTitleRequiredException(message: String = "제목을 입력해 주세요.") : CommunityUserException("POST_TITLE_REQUIRED", message)

class PostTitleLengthInvalidException(message: String = "제목은 50자를 넘을 수 없습니다.") : CommunityUserException("POST_TITLE_LENGTH_INVALID", message)

class PostBodyRequiredException(message: String = "본문을 입력해 주세요.") : CommunityUserException("POST_BODY_REQUIRED", message)

class PostBodyLengthInvalidException(message: String = "본문은 500자를 넘을 수 없습니다.") : CommunityUserException("POST_BODY_LENGTH_INVALID", message)

class InvalidTagException(message: String = "태그는 공백 없이 30자 이내여야 합니다.") : CommunityUserException("INVALID_TAG", message)

class TooManyTagsException(message: String = "태그는 최대 5개입니다.") : CommunityUserException("TOO_MANY_TAGS", message)

class HiddenPostNotEditableException(message: String = "숨겨진 글은 수정할 수 없습니다.") : CommunityUserException("POST_NOT_EDITABLE", message)

class NotPostOwnerException(message: String = "본인의 글만 삭제할 수 있습니다.") : CommunityUserException("NOT_POST_OWNER", message)
