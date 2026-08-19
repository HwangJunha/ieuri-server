package org.around.ieuri.alarm.domain.vo

/**
 * 기기 하나로 나갈 알림 한 건(SPEC-0028 R11·R13).
 *
 * [title]은 서버가 조립해 수신자 언어로 **번역한 문구**이고, [body]는 사용자가 쓴 원문을
 * 번역 없이 자른 것이다. 둘의 언어가 다를 수 있으며 그것이 의도다.
 */
data class PushNotification(
    val title: String,
    val body: String,
    val destination: PushDestination,
)

/**
 * 알림 제목 문구(POL-022 — 서버가 조립하는 문구만 4개 언어).
 *
 * 본문은 여기 없다 — 사용자의 말은 번역하지 않는다(R13·AC13.1).
 */
object PushMessages {
    /**
     * 푸시 본문 상한. 넘으면 자른다(AC13.2).
     *
     * FCM 자체 제한(데이터 메시지 4KB)이 아니라 **알림 표시 영역의 실용 상한**이다 — iOS와
     * Android 모두 두어 줄만 보여 주고 나머지는 잘라 낸다. 서버가 먼저 잘라 두면 전송량도
     * 준다. 잘린 자리는 말줄임표로 표시한다.
     */
    const val BODY_MAX_LENGTH = 200

    private const val ELLIPSIS = "…"

    /**
     * 상한을 넘으면 [BODY_MAX_LENGTH] 길이에 맞춰 자르고 끝에 말줄임표를 붙인다.
     * **결과 길이가 상한을 넘지 않는다** — 말줄임표 한 자를 포함해 센다.
     * 사진만 있는 빈 본문은 빈 문자열 그대로다(AC13.3).
     */
    fun truncateBody(raw: String): String {
        val body = raw.trim()
        if (body.length <= BODY_MAX_LENGTH) return body
        return body.take(BODY_MAX_LENGTH - ELLIPSIS.length) + ELLIPSIS
    }

    /** DM 도착 제목. 보낸 사람의 닉네임을 그대로 쓴다. */
    fun dmTitle(locale: AlarmLocale, senderNickname: String): String = when (locale) {
        AlarmLocale.KO -> "${senderNickname}님이 메시지를 보냈어요"
        AlarmLocale.JA -> "${senderNickname}さんからメッセージが届きました"
        AlarmLocale.ZH -> "${senderNickname} 傳送了訊息"
        AlarmLocale.EN -> "$senderNickname sent you a message"
    }

    /** 닉네임을 읽지 못했을 때의 DM 제목. 알림을 거르는 것보다 이름 없이라도 보내는 쪽이 낫다. */
    fun dmTitleWithoutSender(locale: AlarmLocale): String = when (locale) {
        AlarmLocale.KO -> "새 메시지가 도착했어요"
        AlarmLocale.JA -> "新しいメッセージが届きました"
        AlarmLocale.ZH -> "您有新訊息"
        AlarmLocale.EN -> "You have a new message"
    }

    /** 내 질문에 답변이 달렸을 때의 제목. */
    fun answerTitle(locale: AlarmLocale): String = when (locale) {
        AlarmLocale.KO -> "내 질문에 답변이 달렸어요"
        AlarmLocale.JA -> "あなたの質問に回答がつきました"
        AlarmLocale.ZH -> "您的提問有了新回覆"
        AlarmLocale.EN -> "Your question has a new answer"
    }

    /** 내 답변에 답글이 달렸을 때의 제목. */
    fun replyTitle(locale: AlarmLocale): String = when (locale) {
        AlarmLocale.KO -> "내 답변에 답글이 달렸어요"
        AlarmLocale.JA -> "あなたの回答に返信がつきました"
        AlarmLocale.ZH -> "您的回覆有了新留言"
        AlarmLocale.EN -> "Your answer has a new reply"
    }
}
