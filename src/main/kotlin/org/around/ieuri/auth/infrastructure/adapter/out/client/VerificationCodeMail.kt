package org.around.ieuri.auth.infrastructure.adapter.out.client

import org.springframework.web.util.HtmlUtils

/**
 * 인증 코드 메일의 제목과 본문(HTML + 평문 대체본).
 * - 스타일은 전부 인라인이다. 다수 클라이언트가 `<style>`과 외부 리소스를 제거한다.
 * - 끼워 넣는 값은 출처와 무관하게 이스케이프한다.
 */
data class VerificationCodeMail(
    val subject: String,
    val plainText: String,
    val html: String,
)

/** 브랜드 색(k-trip-spot `AppColors.primary`)과 맞춘다. */
private const val BRAND_COLOR = "#EA4A3F"

private const val TEXT_COLOR = "#1A1A1A"
private const val MUTED_COLOR = "#6B6B6B"
private const val BORDER_COLOR = "#E5E5E5"
private const val CODE_BACKGROUND = "#F7F7F7"
private const val PAGE_BACKGROUND = "#F2F2F2"

private const val FONT_STACK =
    "-apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Malgun Gothic', 'Segoe UI', Roboto, sans-serif"

/** 이메일 인증 코드 메일. */
fun verificationCodeMail(code: String): VerificationCodeMail = codeMail(
    subject = "[이으리] 이메일 인증 코드",
    heading = "이메일 인증",
    code = code,
)

/** 비밀번호 재설정 코드 메일. */
fun passwordResetCodeMail(code: String): VerificationCodeMail = codeMail(
    subject = "[이으리] 비밀번호 재설정 코드",
    heading = "비밀번호 재설정",
    code = code,
)

private const val LEAD = "아래 인증 코드를 입력해 주세요."
private const val DISCLAIMER = "요청하지 않으셨다면 이 메일을 무시하셔도 됩니다."
private const val FOOTER = "이 메일은 발신 전용입니다. 회신하셔도 답변드릴 수 없습니다."

private fun codeMail(subject: String, heading: String, code: String) = VerificationCodeMail(
    subject = subject,
    plainText = """
        $heading

        $LEAD

        $code

        $DISCLAIMER

        $FOOTER
    """.trimIndent(),
    html = codeMailHtml(heading, code),
)

private fun codeMailHtml(heading: String, code: String): String = """
<div style="margin:0;padding:24px 12px;background-color:$PAGE_BACKGROUND;font-family:$FONT_STACK;">
  <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:collapse;">
    <tr>
      <td align="center">
        <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="480" style="width:480px;max-width:100%;border-collapse:collapse;background-color:#FFFFFF;border:1px solid $BORDER_COLOR;border-radius:8px;">
          <tr>
            <td style="padding:24px 32px;border-bottom:1px solid $BORDER_COLOR;">
              <span style="font-size:18px;font-weight:700;color:$BRAND_COLOR;letter-spacing:-0.2px;">이으리</span>
            </td>
          </tr>
          <tr>
            <td style="padding:32px;">
              <p style="margin:0 0 8px;font-size:20px;font-weight:700;color:$TEXT_COLOR;">${HtmlUtils.htmlEscape(heading)}</p>
              <p style="margin:0;font-size:14px;line-height:22px;color:$MUTED_COLOR;">${HtmlUtils.htmlEscape(LEAD)}</p>
              <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%" style="border-collapse:collapse;margin:24px 0;">
                <tr>
                  <td align="center" style="padding:20px 16px;background-color:$CODE_BACKGROUND;border:1px solid $BORDER_COLOR;border-radius:6px;">
                    <span style="font-size:28px;font-weight:700;letter-spacing:6px;color:$TEXT_COLOR;">${HtmlUtils.htmlEscape(code)}</span>
                  </td>
                </tr>
              </table>
              <p style="margin:0;font-size:13px;line-height:20px;color:$MUTED_COLOR;">$DISCLAIMER</p>
            </td>
          </tr>
          <tr>
            <td style="padding:16px 32px;border-top:1px solid $BORDER_COLOR;">
              <p style="margin:0;font-size:12px;line-height:18px;color:$MUTED_COLOR;">$FOOTER</p>
            </td>
          </tr>
        </table>
      </td>
    </tr>
  </table>
</div>
""".trimIndent()
