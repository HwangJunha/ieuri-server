package org.around.ieuri.tourism.application.detail

import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.tourism.application.detail.required.SpotDetailRepository
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.SpotCategory
import kotlin.test.Test

/** write-behind 적재가 갈래마다 같은 spot의 저장소 연산으로 가는지 본다. */
class SpotDetailModifyServiceTest {

    private val repository = mockk<SpotDetailRepository>(relaxed = true)
    private val service = SpotDetailModifyService(repository)

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

    @Test
    fun `본문과 본문 부재는 서로 다른 적재로 간다`() {
        service.saveDetail(1L, detail)
        service.saveDetailAbsence(2L, detail)

        verify(exactly = 1) { repository.saveDetail(1L, detail) }
        verify(exactly = 1) { repository.saveDetailAbsence(2L, detail) }
        verify(exactly = 0) { repository.saveDetail(2L, any()) }
    }

    @Test
    fun `갤러리·운영 정보·이용안내는 각자의 적재로 간다`() {
        val gallery = listOf(SpotImage("https://img/1.jpg", null, null, CopyrightType.TYPE3))
        val intro = SpotIntro("09-18", null, null, null, null, null, null)
        val items = listOf(SpotInfoItem("입장료", "무료"))
        val category = SpotCategory("TOURIST_SPOT")

        service.saveGallery(3L, gallery)
        service.saveIntro(4L, category, intro)
        service.saveInfoItems(5L, category, items)

        verify(exactly = 1) { repository.saveGallery(3L, gallery) }
        verify(exactly = 1) { repository.saveIntro(4L, category, intro) }
        verify(exactly = 1) { repository.saveInfoItems(5L, category, items) }
    }
}
