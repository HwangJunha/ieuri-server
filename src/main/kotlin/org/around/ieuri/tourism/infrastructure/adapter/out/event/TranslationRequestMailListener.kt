package org.around.ieuri.tourism.infrastructure.adapter.out.event

import jakarta.mail.internet.MimeMessage
import org.around.ieuri.tourism.application.event.TranslationTargetsFound
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.time.LocalDate

/**
 * 번역 대상 목록을 JSON 첨부로 보낸다(SPEC-0019 R-4).
 *
 * **본문이 아니라 첨부다.** 대상이 수천 건이라 JSON이 수백 KB가 되고, 본문에 넣으면 메일
 * 클라이언트가 자르거나 줄바꿈을 넣어 파싱이 깨진다.
 *
 * `@EventListener`이며 `@TransactionalEventListener`가 아니다 — 발행 지점이 읽기 전용
 * 트랜잭션이라 AFTER_COMMIT이 오지 않는다.
 *
 * **발송 실패가 배치를 실패시키지 않는다** — 대상 조회는 이미 끝났고 알림은 부수적이다.
 * 여기서 예외를 올리면 성공한 Step이 실패로 기록된다. 재시도하지 않으므로 실패한 메일은
 * 유실되며, 다음 실행이 같은 대상을 다시 찾아 보낸다(번역이 채워지기 전까지 대상은 남는다).
 */
@Component
class TranslationRequestMailListener(
    private val mailSender: JavaMailSender,
    private val objectMapper: ObjectMapper,
    @Value("\${ieuri.mail.from:}") private val fromAddress: String,
    @Value("\${ieuri.catalog.translation-request-mail-to:}") private val toAddress: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun on(event: TranslationTargetsFound) {
        if (toAddress.isBlank()) {
            log.info("translation request mail skipped: no recipient configured (targets={})", event.targets.size)
            return
        }

        try {
            val json = objectMapper.writeValueAsBytes(event.targets)
            val message: MimeMessage = mailSender.createMimeMessage()
            // multipart=true여야 첨부를 붙일 수 있다.
            val helper = MimeMessageHelper(message, true, Charsets.UTF_8.name())

            if (fromAddress.isNotBlank()) helper.setFrom(fromAddress)
            helper.setTo(toAddress)
            helper.setSubject("[이으리] 번역 필요 ${event.targets.size}건 (${LocalDate.now()})")
            helper.setText(
                """
                번역이 필요한 카탈로그 ${event.targets.size}건입니다.

                첨부 JSON의 각 항목:
                  catalogId        번역을 채울 대상 id
                  koreanTitle      번역 원문(제목)
                  koreanAddress    번역 원문(주소). 원문에 주소가 없으면 null
                  missingLanguages 채워야 할 언어
                """.trimIndent(),
            )
            helper.addAttachment(
                "translation-targets-${LocalDate.now()}.json",
                { json.inputStream() },
                "application/json",
            )

            mailSender.send(message)
            log.info("translation request mail sent: targets={}", event.targets.size)
        } catch (ex: Exception) {
            // 알림 실패로 배정·조회 결과를 되돌릴 이유가 없다.
            log.error("translation request mail failed: targets={}", event.targets.size, ex)
        }
    }
}
