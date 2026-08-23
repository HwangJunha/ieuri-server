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
            // SPEC-0029 R-1. 진료과는 한글 부분 일치라 `외과` 하나가 정형외과·성형외과를 덮는다.
            "외과", "내과", "피부과", "안과", "이비인후과", "산부인과", "소아과", "정신과",
            "비뇨기과", "재활의학과", "영상의학과", "마취통증의학과",
            "보건소", "의료원", "요양원", "요양병원", "검진센터", "산후조리원", "동물병원",
            "Medical Center", "Oriental Medicine", "Veterinary",
        ),
        branchNamePatterns = listOf(
            "병원점", "병원 지점", "Hospital Branch", "의원점", "약국점", "Clinic Branch",
        ),
    )

    /** SHOPPING은 보호 카테고리가 아니라 판정이 그대로 걸린다 — 실측 216건 전량이 이 카테고리다. */
    private fun excluded(title: String, category: SpotCategory = SpotCategory("SHOPPING")) =
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
        assertFalse(excluded("서울대학교병원의학박물관", SpotCategory("CULTURAL_FACILITY")))
    }

    @Test
    fun `보호 대상이 아닌 카테고리는 그대로 걸린다`() {
        // 같은 제목이라도 카테고리가 다르면 판정이 뒤집힌다 — 보호가 카테고리에 걸린다는 증거다.
        assertTrue(excluded("서울 대한의원", SpotCategory("SHOPPING")))
        assertTrue(excluded("최혁한의원", SpotCategory("RESTAURANT")))
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

        assertFalse(empty.isExcluded("명동명약국", SpotCategory("SHOPPING")))
        assertFalse(empty.isExcluded("CU 종로점", SpotCategory("SHOPPING")))
    }

    @Test
    fun `빈 문자열 키워드는 버려진다`() {
        // 남겨 두면 모든 제목에 일치해 전량 비활성이 된다.
        val blanks = SpotExclusionRule(keywords = listOf("", "  "), branchNamePatterns = emptyList())

        assertFalse(blanks.isExcluded("경복궁", SpotCategory("SHOPPING")))
    }

    @Test
    fun `빈 문자열이 섞여 있어도 나머지 키워드는 살아 있다`() {
        val mixed = SpotExclusionRule(keywords = listOf("", "약국"), branchNamePatterns = emptyList())

        assertTrue(mixed.isExcluded("명동명약국", SpotCategory("SHOPPING")))
        assertFalse(mixed.isExcluded("경복궁", SpotCategory("SHOPPING")))
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

    // ── SPEC-0029 R-1: 의료 시설 ────────────────────────────────────────────

    @Test
    fun `외과 한 항목이 모든 외과 진료과를 잡는다`() {
        // AC-1.1. 한글이 부분 일치이므로 진료과를 개별 나열할 필요가 없다 — 이것이
        // 목록에 `정형외과`·`성형외과`가 없는 이유다. 나열로 바뀌면 여기서 깨진다.
        assertTrue(excluded("서울정형외과의원"))
        assertTrue(excluded("강남성형외과"))
        assertTrue(excluded("우리신경외과"))
        assertTrue(excluded("부산흉부외과"))
    }

    @Test
    fun `진료과와 의료 시설이 제외된다`() {
        // AC-1.2
        assertTrue(excluded("강남피부과"))
        assertTrue(excluded("밝은안과"))
        assertTrue(excluded("연세이비인후과"))
        assertTrue(excluded("행복산부인과"))
        assertTrue(excluded("종로구보건소"))
        assertTrue(excluded("제주의료원"))
        assertTrue(excluded("실버요양원"))
        assertTrue(excluded("서울요양병원"))
        assertTrue(excluded("강남종합검진센터"))
        assertTrue(excluded("미소산후조리원"))
        assertTrue(excluded("행복동물병원"))
    }

    @Test
    fun `영문 의료 키워드는 단어 경계로 걸린다`() {
        assertTrue(excluded("Seoul Medical Center"))
        assertTrue(excluded("Korean Oriental Medicine Clinic"))
        assertTrue(excluded("Happy Veterinary Hospital"))
    }

    @Test
    fun `의료 키워드가 걸려도 관광 카테고리는 보호된다`() {
        // AC-1.3. 서울대학교병원의학박물관은 실제 관광 대상이다 — 카테고리 보호가 1차 방어선이며,
        // `안과`·`내과`의 오탐 위험을 감당하는 근거이기도 하다.
        assertFalse(excluded("서울대학교병원의학박물관", SpotCategory("CULTURAL_FACILITY")))
        assertFalse(excluded("제중원 한의원 전시관", SpotCategory.TOURIST_SPOT))
    }

    @Test
    fun `의료 시설에 입점한 소매점은 지점명 보호로 살아난다`() {
        // AC-1.4
        assertFalse(excluded("올리브영 제주대학병원점"))
        assertFalse(excluded("탠저비 한라병원점"))
    }

    @Test
    fun `문서화된 오탐 위험이 실제로 존재한다`() {
        // **이 테스트는 걸리는 것이 정상이다.** `안과`·`내과`가 학회관·대학을 잡는 것은
        // 설정 주석과 스펙 §2.1이 사전에 인정한 위험이며, 실측 표본이 없어 지금은 좁히지
        // 않기로 했다. 배포 후 전환 건수가 급증하면 그 키워드를 좁히고 이 테스트를 뒤집는다.
        //
        // 부작용을 테스트로 고정해 두는 이유는, 나중에 누군가 이 동작을 보고 "버그다"라고
        // 판단하기 전에 그것이 알려진 선택이었음을 알 수 있게 하려는 것이다.
        assertTrue(excluded("대한안과학회관"))
        assertTrue(excluded("서울대학교 내과대학"))
    }
}
