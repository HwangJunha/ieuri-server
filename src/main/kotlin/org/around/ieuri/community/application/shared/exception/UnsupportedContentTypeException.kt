package org.around.ieuri.community.application.shared.exception

import org.around.ieuri.community.domain.shared.CommunityUserException

class UnsupportedContentTypeException(message: String = "지원하지 않는 이미지 형식입니다.") : CommunityUserException("UNSUPPORTED_CONTENT_TYPE", message)
