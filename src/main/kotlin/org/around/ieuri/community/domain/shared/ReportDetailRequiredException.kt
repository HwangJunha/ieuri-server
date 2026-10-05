package org.around.ieuri.community.domain.shared

/** 상세 사유는 로그·에러 메시지에 싣지 않는다(POL-013) — 이 메시지에도 담기지 않는다. */
class ReportDetailRequiredException(message: String = "상세 사유를 입력해 주세요.") : CommunityUserException("REPORT_DETAIL_REQUIRED", message)
