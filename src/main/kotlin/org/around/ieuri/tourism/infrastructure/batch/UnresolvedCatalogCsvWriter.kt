package org.around.ieuri.tourism.infrastructure.batch

import org.around.ieuri.tourism.application.port.out.query.UnresolvedCandidate
import org.around.ieuri.tourism.application.port.out.query.UnresolvedSpotReport
import java.nio.file.Files
import java.nio.file.Path

/**
 * 미해결 건 CSV 작성(SPEC-0019 R-8).
 *
 * **메일 발송을 구현하지 않는다.** 파일을 어떻게 가져갈지는 운영자가 정한다 — 발송 경로를
 * 코드가 규정하면 그것을 바꿀 때마다 코드를 고쳐야 한다.
 *
 * 개인정보가 없다(spot 데이터뿐)이므로 마스킹 대상이 아니다(POL-013).
 */
object UnresolvedCatalogCsvWriter {

    /** 헤더. 사람이 스프레드시트로 열어 판정하므로 컬럼 순서가 곧 읽는 순서다. */
    private val HEADER = listOf(
        "language", "spot_id", "title", "category", "map_x", "map_y",
        "reason", "extracted_key", "candidate_count", "candidates", "thumbnail_image_url",
    )

    /** 후보 하나를 한 셀 안에 담는 표기. `id@좌표(카테고리)` 꼴이다. */
    private fun UnresolvedCandidate.render(): String = "$spotId@$mapX,$mapY($category)"

    fun write(reports: List<UnresolvedSpotReport>, target: Path) {
        target.parent?.let(Files::createDirectories)

        Files.newBufferedWriter(target).use { writer ->
            writer.appendLine(HEADER.joinToString(","))

            reports.forEach { report ->
                writer.appendLine(
                    listOf(
                        report.language.name,
                        report.spotId.toString(),
                        report.title,
                        report.category.name,
                        report.mapX.toString(),
                        report.mapY.toString(),
                        report.reason,
                        report.extractedKey.orEmpty(),
                        report.candidates.size.toString(),
                        report.candidates.joinToString(" | ") { it.render() },
                        report.thumbnailImageUrl,
                    ).joinToString(",", transform = ::escape),
                )
            }
        }
    }

    /**
     * RFC 4180 최소 이스케이프. 제목에 쉼표·따옴표·줄바꿈이 실제로 들어 있어
     * (`카페 "봄", 서울점`) 감싸지 않으면 열이 밀린다.
     */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
}
