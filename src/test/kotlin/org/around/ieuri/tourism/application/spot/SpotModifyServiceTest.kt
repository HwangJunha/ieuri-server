package org.around.ieuri.tourism.application.spot

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.spot.provided.exception.SpotOriginNotFoundException
import org.around.ieuri.tourism.application.spot.required.SpotRepository
import org.around.ieuri.tourism.domain.category.SpotCategory
import org.around.ieuri.tourism.domain.shared.Coordinates
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.DomainLocale
import org.around.ieuri.tourism.domain.spot.Spot
import org.around.ieuri.tourism.domain.spot.SpotBody
import org.around.ieuri.tourism.domain.spot.SpotDetail
import org.around.ieuri.tourism.domain.spot.SpotGallery
import org.around.ieuri.tourism.domain.spot.SpotImage
import org.around.ieuri.tourism.domain.spot.SpotInfoItem
import org.around.ieuri.tourism.domain.spot.SpotInformation
import org.around.ieuri.tourism.domain.spot.SpotIntro
import org.around.ieuri.tourism.domain.spot.SpotIntroduction
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 상세 적재는 원본을 읽고, 원본이 만든 갈래별 자식을 그 원본 id로 쓴다. 원본이 없으면 아무것도 쓰지 않는다. */
class SpotModifyServiceTest {

    private val repository = mockk<SpotRepository>(relaxed = true)
    private val service = SpotModifyService(repository)

    private val detail = SpotDetail(
        title = "경복궁",
        category = SpotCategory("TOURIST_SPOT"),
        thumbnailImageUrl = "https://img/thumb.jpg",
        overview = null,
        address = null,
        homepage = null,
        tel = null,
        copyright = CopyrightType.TYPE1,
    )

    private fun spot(id: Long) = Spot(
        contentId = "C-$id",
        language = DomainLocale.KO,
        title = "경복궁",
        address = null,
        thumbnailImageUrl = "https://img/$id.jpg",
        coordinates = Coordinates(latitude = 37.5, longitude = 127.0),
        category = SpotCategory("TOURIST_SPOT"),
        contentTypeId = 12,
        copyright = CopyrightType.TYPE1,
        id = id,
    )

    @Test
    fun `본문과 본문 부재는 부재 여부만 다른 본문으로 간다`() {
        every { repository.findById(1L) } returns spot(1L)
        every { repository.findById(2L) } returns spot(2L)

        service.saveDetail(1L, detail)
        service.saveDetailAbsence(2L, detail)

        verify(exactly = 1) { repository.saveBody(SpotBody(1L, detail, absent = false)) }
        verify(exactly = 1) { repository.saveBody(SpotBody(2L, detail, absent = true)) }
    }

    @Test
    fun `갤러리·운영 정보·이용안내는 각자의 원본 id를 가진 자식으로 간다`() {
        listOf(3L, 4L, 5L).forEach { every { repository.findById(it) } returns spot(it) }
        val gallery = listOf(SpotImage("https://img/1.jpg", null, null, CopyrightType.TYPE3))
        val intro = SpotIntro("09-18", null, null, null, null, null, null)
        val items = listOf(SpotInfoItem("입장료", "무료"))
        val category = SpotCategory("RESTAURANT")

        service.saveGallery(3L, gallery)
        service.saveIntro(4L, category, intro)
        service.saveInfoItems(5L, category, items)

        verify(exactly = 1) { repository.saveGallery(SpotGallery(3L, gallery)) }
        verify(exactly = 1) { repository.saveIntro(SpotIntroduction(4L, category, intro)) }
        verify(exactly = 1) { repository.saveInfoItems(SpotInformation(5L, category, items)) }
    }

    @Test
    fun `원본이 없으면 예외이고 아무것도 쓰지 않는다`() {
        every { repository.findById(9L) } returns null

        assertFailsWith<SpotOriginNotFoundException> { service.saveDetail(9L, detail) }
        assertFailsWith<SpotOriginNotFoundException> { service.saveDetailAbsence(9L, detail) }
        assertFailsWith<SpotOriginNotFoundException> { service.saveIntro(9L, SpotCategory("TOURIST_SPOT"), null) }
        assertFailsWith<SpotOriginNotFoundException> { service.saveGallery(9L, listOf(SpotImage("https://img/1.jpg", null, null, CopyrightType.TYPE3))) }
        assertFailsWith<SpotOriginNotFoundException> { service.saveInfoItems(9L, SpotCategory("TOURIST_SPOT"), listOf(SpotInfoItem("입장료", "무료"))) }

        verify(exactly = 0) { repository.saveBody(any()) }
        verify(exactly = 0) { repository.saveIntro(any()) }
        verify(exactly = 0) { repository.saveGallery(any()) }
        verify(exactly = 0) { repository.saveInfoItems(any()) }
    }

    @Test
    fun `원본이 없어도 빈 갤러리·빈 이용안내는 조용히 끝난다`() {
        every { repository.findById(9L) } returns null

        service.saveGallery(9L, emptyList())
        service.saveInfoItems(9L, SpotCategory("TOURIST_SPOT"), emptyList())

        verify(exactly = 0) { repository.saveGallery(any()) }
        verify(exactly = 0) { repository.saveInfoItems(any()) }
    }
}
