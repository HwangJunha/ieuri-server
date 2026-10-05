package org.around.ieuri.community.domain.shared

class AlreadyReportedException(message: String = "이미 신고한 대상입니다.") : CommunityUserException("ALREADY_REPORTED", message)
