package org.around.ieuri.community.application.event

/**
 * 새 메시지가 저장됐다(SPEC-0017 R14).
 *
 * **브로커 발행을 커밋 뒤로 미루기 위해 존재한다.** 트랜잭션 안에서 발행하면 구독자가
 * 그 id로 DB를 읽는 시점에 아직 커밋 전이라 행이 보이지 않고, 이벤트가 조용히 버려진다 —
 * 커밋과 조회의 경합이라 "가끔 도착"하는 형태로 나타난다.
 *
 * 본문을 싣지 않는 이유는 브로커 페이로드와 같다(POL-013): 값의 원본은 항상 DB다.
 */
data class NewMessageStored(
    val conversationId: Long,
    val messageId: Long,
)
