package org.around.ieuri.architecture

import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 이 슬라이스가 더하거나 바꾼 `.kt` 파일. [MapperShapeTest]의 검사 범위다.
 *
 * **왜 전체가 아니라 변경분인가.** POL-033은 이 코드보다 나중에 생겼고 기존 위반이 34건 있다.
 * 전체를 검사하면 센서가 켜지는 순간 빨갛고, 그러면 `@Ignore`로 꺼지고, 꺼진 센서는 아무것도
 * 막지 못한다. `CLAUDE.md`도 같은 입장을 이미 적어 두었다 — "Conversions predating this rule
 * are untested; that is the accepted current state, and the rule binds what a slice adds or
 * changes."
 *
 * 기존 파일을 고치면 그 순간 검사 대상이 된다. 정리는 그렇게 점진적으로 일어난다.
 *
 * **git이 없거나 실패하면 빈 목록을 돌려준다** — 센서가 통과한다. 검사하지 못한 것을 위반으로
 * 바꾸면 CI 환경 차이로 빌드가 깨지는데, 그것은 이 센서가 잡으려는 문제가 아니다.
 */
object ChangedFiles {

    /** 비교 기준. 기능 브랜치는 `dev`에서 잘라낸다. */
    private const val BASE_REF = "dev"

    private val kotlinPaths: Set<String> by lazy { collect() }

    /** 이 경로가 이번 슬라이스에서 바뀐 파일인가. Konsist가 주는 절대경로로 판정한다. */
    fun contains(absolutePath: String): Boolean =
        kotlinPaths.any { absolutePath.endsWith(it) }

    private fun collect(): Set<String> =
        // 커밋된 변경 + 아직 커밋 안 한 변경 + 새 파일. 셋을 다 봐야 작업 중에도 센서가 돈다.
        (git("diff", "--name-only", "$BASE_REF...HEAD") +
            git("diff", "--name-only", "HEAD") +
            git("ls-files", "--others", "--exclude-standard"))
            .filter { it.endsWith(".kt") }
            .toSet()

    private fun git(vararg args: String): List<String> = runCatching {
        val process = ProcessBuilder(listOf("git") + args)
            .directory(File(System.getProperty("user.dir")))
            .start()
        val output = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(20, TimeUnit.SECONDS) || process.exitValue() != 0) emptyList()
        else output.lines().map { it.trim() }.filter { it.isNotEmpty() }
    }.getOrDefault(emptyList())
}
