package org.around.ieuri.community.adapter.messaging.mapper

/** 새 메시지 알림. id만 담고 본문은 구독 측이 DB에서 읽는다. */
data class NewMessagePayload(val messageId: Long)
