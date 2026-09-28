package org.around.ieuri.architecture

import com.lemonappdev.konsist.api.Konsist
import kotlin.test.Test
import kotlin.test.fail

/** 주석 한 덩어리(KDoc·블록 주석·연속된 `//`)는 15줄 미만이다(SPEC-0039 D-31). 근거는 spec에 둔다. */
class CommentLengthTest {

    @Test
    fun `주석 한 덩어리는 15줄 미만이다`() {
        // 프로젝트 루트 기준: 자동 생성 코드(build/)와 다른 worktree(.claude/)는 검사 대상이 아니다.
        val sources = Konsist.scopeFromProject().files.filterNot {
            it.projectPath.startsWith("/build/") || it.projectPath.startsWith("/.claude/")
        }
        val violations = sources.flatMap { file ->
            commentBlocks(file.text)
                .filter { it.lines >= MAX_LINES }
                .map { "${file.path}:${it.startLine} (${it.lines}줄)" }
        }
        if (violations.isNotEmpty()) {
            fail(
                """
                [konsist] 15줄 이상인 주석이 있습니다.
                  → 무엇을 하는지 한두 줄, 운영 경고와 동작 계약만 한 줄씩 남기고 근거·이력은 spec으로 옮긴다.
                ${violations.joinToString("\n") { "  $it" }}
                """.trimIndent(),
            )
        }
    }

    private data class CommentBlock(val startLine: Int, val lines: Int)

    /** 문자열·문자 리터럴 안의 주석 기호는 주석으로 세지 않는다. */
    private fun commentBlocks(src: String): List<CommentBlock> {
        val blocks = mutableListOf<CommentBlock>()
        var i = 0
        var line = 1
        var runStart = -1
        var runLength = 0
        var lastCommentLine = -1

        fun flushRun() {
            if (runStart > 0) blocks += CommentBlock(runStart, runLength)
            runStart = -1
            runLength = 0
        }

        while (i < src.length) {
            when {
                src[i] == '\n' -> {
                    line++
                    i++
                }

                src.startsWith("\"\"\"", i) -> {
                    val close = src.indexOf("\"\"\"", i + 3)
                    val end = if (close < 0) src.length else close + 3
                    line += src.substring(i, end).count { it == '\n' }
                    i = end
                }

                src[i] == '"' -> i = skipLineString(src, i)

                src[i] == '\'' -> i = skipCharLiteral(src, i)

                src.startsWith("//", i) -> {
                    if (lastCommentLine == line - 1 && runStart > 0) {
                        runLength++
                    } else {
                        flushRun()
                        runStart = line
                        runLength = 1
                    }
                    lastCommentLine = line
                    val newline = src.indexOf('\n', i)
                    i = if (newline < 0) src.length else newline
                }

                src.startsWith("/*", i) -> {
                    val end = blockCommentEnd(src, i)
                    val lines = src.substring(i, end).count { it == '\n' } + 1
                    blocks += CommentBlock(line, lines)
                    line += lines - 1
                    i = end
                }

                else -> i++
            }
        }
        flushRun()
        return blocks
    }

    private fun skipLineString(src: String, start: Int): Int {
        var i = start + 1
        while (i < src.length && src[i] != '"' && src[i] != '\n') i += if (src[i] == '\\') 2 else 1
        return i + 1
    }

    private fun skipCharLiteral(src: String, start: Int): Int {
        val close = src.indexOf('\'', start + if (src.getOrNull(start + 1) == '\\') 3 else 2)
        return if (close in start + 2..start + 8) close + 1 else start + 1
    }

    /** Kotlin 블록 주석은 중첩된다. */
    private fun blockCommentEnd(src: String, start: Int): Int {
        var depth = 1
        var i = start + 2
        while (i < src.length && depth > 0) {
            when {
                src.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }

                src.startsWith("*/", i) -> {
                    depth--
                    i += 2
                }

                else -> i++
            }
        }
        return i
    }

    companion object {
        private const val MAX_LINES = 15
    }
}
