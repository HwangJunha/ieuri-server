package org.around.ieuri.tourism.domain.specification

import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 비관광 spot 제외 판정(SPEC-0018 R4~R7·R12).
 *
 * 실제 설정에 들어가는 목록으로 검사한다 — 규칙만 맞고 목록이 틀리면 아무것도 막지 못한다.
 */
class SpotExclusionRuleTest {

    private val rule = SpotExclusionRule(
        keywords = listOf(
            "약국", "Pharmacy",
            "CU", "씨유", "GS25", "세븐일레븐", "7-Eleven", "이마트24", "emart24", "E-Mart 24",
            "미니스톱", "MINISTOP", "스토리웨이",
            "의원", "한의원", "Clinic", "Dermatology", "병원", "Hospital", "치과", "Dental",
        ),
        branchNamePatterns = listOf(
            "병원점", "병원 지점", "Hospital Branch", "의원점", "약국점", "Clinic Branch",
        ),
    )

    /** SHOPPING은 보호 카테고리가 아니라 판정이 그대로 걸린다 — 실측 216건 전량이 이 카테고리다. */
    private fun excluded(title: String, category: SpotCategory = SpotCategory.SHOPPING) =
        rule.isExcluded(title, category)

    // ── R4: 키워드 일치 ──────────────────────────────────────────────────────

    @Test
    fun `한글 키워드는 제목 어디에 있어도 걸린다`() {
        // 교착어라 접사 없이 붙는다 — 단어 경계를 두면 이 형태를 놓친다.
        assertTrue(excluded("명동명약국"))
        assertTrue(excluded("온누리약국 강남점"))
        assertTrue(excluded("연세미소치과의원"))
    }

    @Test
    fun `영문 키워드는 대소문자를 가리지 않는다`() {
        assertTrue(excluded("Jongno Pharmacy"))
        assertTrue(excluded("JONGNO PHARMACY"))
        assertTrue(excluded("emart24 Seoul"))
        assertTrue(excluded("EMART24 Seoul"))
    }

    @Test
    fun `제목 전체를 검사한다`() {
        // R7 — 앞뒤 어디에 있든 걸려야 한다.
        assertTrue(excluded("약국 하나로"))
        assertTrue(excluded("하나로 약국"))
        assertTrue(excluded("하나로 약국 본점"))
    }

    // ── R4: 단어 경계 (가장 위험한 항목이 CU다) ─────────────────────────────

    @Test
    fun `CU는 단어로 쓰였을 때만 걸린다`() {
        assertTrue(excluded("CU 종로점"))
        assertTrue(excluded("cu 종로점"))
    }

    @Test
    fun `단어 안에 든 cu는 걸리지 않는다`() {
        // 경계가 없으면 실측 표본에서 52건이 아니라 262건이 걸리고, 늘어난 210건은 전부 오탐이다.
        assertFalse(excluded("인사동 문화의 거리"))
        assertFalse(excluded("문화역 서울 284"))
        assertFalse(excluded("동대문역사문화공원"))
        assertFalse(excluded("Joint Security Area"))
    }

    @Test
    fun `Pharmaceutical 같은 정상 상호는 걸리지 않는다`() {
        // `Pharm` 어간을 넣지 않은 이유다 — 단어 경계가 이것을 지킨다.
        assertFalse(excluded("Hanmi Pharmaceutical"))
    }

    // ── R5: 관광 카테고리 보호 ──────────────────────────────────────────────

    @Test
    fun `TOURIST_SPOT은 키워드에 걸려도 제외하지 않는다`() {
        // 대한제국기 의료기관 건물(사적)·한의원 등 실재하는 관광지다.
        assertFalse(excluded("서울 대한의원", SpotCategory.TOURIST_SPOT))
        assertFalse(excluded("최혁한의원", SpotCategory.TOURIST_SPOT))
        assertFalse(excluded("의료법인 서전의료재단 한사랑병원", SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `CULTURAL_FACILITY도 보호한다`() {
        assertFalse(excluded("서울대학교병원의학박물관", SpotCategory.CULTURAL_FACILITY))
    }

    @Test
    fun `보호 대상이 아닌 카테고리는 그대로 걸린다`() {
        // 같은 제목이라도 카테고리가 다르면 판정이 뒤집힌다 — 보호가 카테고리에 걸린다는 증거다.
        assertTrue(excluded("서울 대한의원", SpotCategory.SHOPPING))
        assertTrue(excluded("최혁한의원", SpotCategory.RESTAURANT))
    }

    // ── R6: 지점명 보호 ─────────────────────────────────────────────────────

    @Test
    fun `병원에 입점한 소매점은 지점명 보호로 되살아난다`() {
        // 실측 3건이 전부 화장품 매장이었다.
        assertFalse(excluded("올리브영 제주대학병원점"))
        assertFalse(excluded("탠저비 한라병원점"))
        assertFalse(excluded("Olive Young - Cheju Halla General Hospital Branch"))
    }

    @Test
    fun `지점명 보호는 대소문자를 가리지 않는다`() {
        assertFalse(excluded("Olive Young - Halla hospital branch"))
    }

    @Test
    fun `지점명 보호가 병원 자체를 되살리지는 않는다`() {
        // "병원점"이 아니라 "병원"으로 끝나면 보호 대상이 아니다.
        assertTrue(excluded("한라병원"))
    }

    // ── R12: 빈 목록 ────────────────────────────────────────────────────────

    @Test
    fun `키워드 목록이 비면 아무것도 제외하지 않는다`() {
        // 빈 목록을 "전부 제외"로 읽으면 설정 실수 한 번이 전 행을 비활성으로 만든다.
        val empty = SpotExclusionRule(keywords = emptyList(), branchNamePatterns = emptyList())

        assertFalse(empty.isExcluded("명동명약국", SpotCategory.SHOPPING))
        assertFalse(empty.isExcluded("CU 종로점", SpotCategory.SHOPPING))
    }

    @Test
    fun `빈 문자열 키워드는 버려진다`() {
        // 남겨 두면 모든 제목에 일치해 전량 비활성이 된다.
        val blanks = SpotExclusionRule(keywords = listOf("", "  "), branchNamePatterns = emptyList())

        assertFalse(blanks.isExcluded("경복궁", SpotCategory.SHOPPING))
    }

    @Test
    fun `빈 문자열이 섞여 있어도 나머지 키워드는 살아 있다`() {
        val mixed = SpotExclusionRule(keywords = listOf("", "약국"), branchNamePatterns = emptyList())

        assertTrue(mixed.isExcluded("명동명약국", SpotCategory.SHOPPING))
        assertFalse(mixed.isExcluded("경복궁", SpotCategory.SHOPPING))
    }

    // ── 정상 관광지가 걸리지 않는다 ─────────────────────────────────────────

    @Test
    fun `관광지 제목은 걸리지 않는다`() {
        assertFalse(excluded("경복궁"))
        assertFalse(excluded("Gyeongbokgung Palace"))
        assertFalse(excluded("남산서울타워"))
        assertFalse(excluded("현대프리미엄아울렛 김포점"))
    }

    @Test
    fun `특수문자가 든 키워드도 리터럴로 취급한다`() {
        // `7-Eleven`·`E-Mart 24`의 하이픈이 정규식 메타로 해석되면 조용히 어긋난다.
        assertTrue(excluded("7-Eleven 종로점"))
        assertTrue(excluded("E-Mart 24 강남"))
    }
}
