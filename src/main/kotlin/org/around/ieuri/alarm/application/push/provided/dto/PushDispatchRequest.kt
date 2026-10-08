package org.around.ieuri.alarm.application.push.provided.dto

import org.around.ieuri.alarm.domain.push.PushDestination

/** 발송 요청 한 건. 본문은 없고, 문구는 목적지 종류와 기기 언어로 DB에서 찾는다(R13). */
data class PushDispatchRequest(
    val recipientId: Long,
    /** 알림을 유발한 사람. [recipientId]와 같으면 발송하지 않는다(R8). */
    val actorId: Long,
    val destination: PushDestination,
)
