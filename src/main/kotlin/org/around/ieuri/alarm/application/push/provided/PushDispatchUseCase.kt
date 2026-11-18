package org.around.ieuri.alarm.application.push.provided

import org.around.ieuri.alarm.application.push.provided.dto.PushDispatchRequest

/** 푸시 발송(SPEC-0028 R5~R13). 다른 도메인의 이벤트를 인바운드 어댑터가 번역해 넣는다. */
interface PushDispatchUseCase {
    /**
     * 수신자에게 알림을 보낸다. 자기 행위 제외·차단·카테고리 게이팅과 무효 토큰 제거까지 한다.
     * - 걸러져 발송하지 않아도 예외 없이 조용히 끝난다.
     */
    fun dispatch(request: PushDispatchRequest)
}
