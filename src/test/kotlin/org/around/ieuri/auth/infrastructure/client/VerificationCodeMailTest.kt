package org.around.ieuri.auth.infrastructure.client

import org.around.ieuri.auth.infrastructure.adapter.out.client.passwordResetCodeMail
import org.around.ieuri.auth.infrastructure.adapter.out.client.verificationCodeMail
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 인증 코드 메일 템플릿 검증.
 *
 * 마크업의 생김새는 고정하지 않는다 — 디자인을 손볼 때마다 깨지는 테스트가 되고,
 * 실제 렌더링은 클라이언트마다 다르므로 여기서 증명할 수 있는 것도 아니다.
 * 고정하는 것은 **틀렸을 때 사용자가 코드를 못 받는 것들**이다:
 * 코드가 두 본문에 모두 실리는가, 두 메일이 서로 다른 제목·문구를 쓰는가,
 * HTML이 이메일 클라이언트에서 살아남을 형태인가(인라인 스타일·외부 리소스 없음).
 */
class VerificationCodeMailTest {

    @Test
    fun `이메일 인증 메일은 코드를 HTML과 평문 양쪽에 싣는다`() {
        val mail = verificationCodeMail("341010")

        assertEquals("[이으리] 이메일 인증 코드", mail.subject)
        assertTrue(mail.html.contains("341010"), "HTML 본문에 코드가 없다")
        assertTrue(mail.plainText.contains("341010"), "평문 본문에 코드가 없다")
    }

    @Test
    fun `비밀번호 재설정 메일은 코드를 HTML과 평문 양쪽에 싣는다`() {
        val mail = passwordResetCodeMail("998877")

        assertEquals("[이으리] 비밀번호 재설정 코드", mail.subject)
        assertTrue(mail.html.contains("998877"), "HTML 본문에 코드가 없다")
        assertTrue(mail.plainText.contains("998877"), "평문 본문에 코드가 없다")
    }

    /** 두 메일이 같은 틀을 쓰므로, 목적을 알려 주는 문구가 실제로 갈라지는지 본다. */
    @Test
    fun `두 메일은 제목과 본문 문구가 서로 다르다`() {
        val verification = verificationCodeMail("111111")
        val reset = passwordResetCodeMail("111111")

        assertFalse(verification.subject == reset.subject, "제목이 같으면 목적을 구분할 수 없다")
        assertTrue(verification.html.contains("이메일 인증"))
        assertTrue(reset.html.contains("비밀번호 재설정"))
        assertFalse(reset.html.contains("이메일 인증"), "재설정 메일에 인증 문구가 남았다")
    }

    /** 두 본문 모두에 안내문이 있어야 한다 — 평문만 보는 클라이언트도 있다. */
    @Test
    fun `요청하지 않은 사용자를 위한 안내문이 두 본문에 모두 있다`() {
        val mail = verificationCodeMail("341010")

        assertTrue(mail.html.contains("요청하지 않으셨다면"))
        assertTrue(mail.plainText.contains("요청하지 않으셨다면"))
    }

    /**
     * Gmail을 비롯한 클라이언트가 `<style>` 블록과 외부 리소스를 제거하므로,
     * 그런 것에 의존하면 스타일이 통째로 날아간다.
     */
    @Test
    fun `HTML은 인라인 스타일만 쓰고 외부 리소스를 참조하지 않는다`() {
        val html = verificationCodeMail("341010").html

        assertFalse(html.contains("<style"), "style 블록은 제거되는 클라이언트가 있다")
        assertFalse(html.contains("<script"), "script는 이메일에서 실행되지 않는다")
        assertFalse(html.contains("<img"), "외부 이미지는 기본 차단된다")
        assertFalse(html.contains("http://") || html.contains("https://"), "외부 리소스를 참조한다")
        assertTrue(html.contains("style=\""), "인라인 스타일이 없다")
    }

    /** 코드는 숫자로만 오지만, 값의 출처를 신뢰 근거로 삼지 않는다. */
    @Test
    fun `본문에 실리는 값은 HTML 이스케이프된다`() {
        val html = verificationCodeMail("<script>x</script>").html

        assertFalse(html.contains("<script>x</script>"), "태그가 그대로 실렸다")
        assertTrue(html.contains("&lt;script&gt;x&lt;/script&gt;"))
    }

    /** 평문은 HTML을 막아 둔 클라이언트가 보는 유일한 본문이다. */
    @Test
    fun `평문 본문에는 마크업이 섞이지 않는다`() {
        val plainText = verificationCodeMail("341010").plainText

        assertFalse(plainText.contains("<"), "평문에 태그가 섞였다")
        assertFalse(plainText.contains("style="), "평문에 스타일이 섞였다")
    }
}
