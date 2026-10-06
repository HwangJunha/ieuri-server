package org.around.ieuri.tourism.adapter.client.tourapi.mapper

import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailCommonResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailImageResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailInfoResponse
import org.around.ieuri.tourism.adapter.client.tourapi.TourApiDetailIntroResponse
import org.around.ieuri.tourism.domain.detail.SpotDetail
import org.around.ieuri.tourism.domain.detail.SpotImage
import org.around.ieuri.tourism.domain.detail.SpotInfoItem
import org.around.ieuri.tourism.domain.detail.SpotIntro
import org.around.ieuri.tourism.domain.shared.CopyrightType
import org.around.ieuri.tourism.domain.shared.SpotCategory

/** detailCommon2 item → 도메인 [SpotDetail] 본문. [category]는 호출자가 언어별 코드표로 해석해 넘긴다. */
fun TourApiDetailCommonResponse.Item.toSpotDetail(category: SpotCategory): SpotDetail = SpotDetail(
    title = title.orEmpty(),
    category = category,
    thumbnailImageUrl = firstimage.toHttps(),
    overview = overview,
    address = listOfNotNull(addr1, addr2).joinToString(" ").ifBlank { null },
    homepage = homepage,
    tel = tel,
    copyright = CopyrightType.fromCode(cpyrhtDivCd),
)

/** detailImage2 item 목록을 갤러리로 옮긴다. 반환 순서를 유지하고 앞 10장까지만 남긴다. */
fun List<TourApiDetailImageResponse.Item>.toGallery(): List<SpotImage> = take(SpotDetail.MAX_GALLERY_SIZE).map { it.toSpotImage() }

/** detailImage2 item을 도메인 [SpotImage]로 옮긴다. */
fun TourApiDetailImageResponse.Item.toSpotImage(): SpotImage = SpotImage(
    imageUrl = originimgurl.toHttps(),
    thumbnailUrl = smallimageurl?.toHttps(),
    caption = imgname,
    copyright = CopyrightType.fromCode(cpyrhtDivCd),
)

/**
 * detailIntro2 item → 도메인 [SpotIntro]. 표시할 값이 하나도 없으면 null이다.
 * - 카테고리별 필드 후보를 순서대로 훑어 처음 값이 있는 것을 쓴다.
 */
fun TourApiDetailIntroResponse.Item.toSpotIntroOrNull(): SpotIntro? = SpotIntro(
    businessHours = firstNotBlank(usetime, usetimeculture, usetimefestival, usetimeleports, opentime, opentimefood),
    closedDays = firstNotBlank(restdate, restdateculture, restdateleports, restdateshopping, restdatefood),
    parking = firstNotBlank(parking, parkingculture, parkingleports, parkinglodging, parkingshopping, parkingfood),
    inquiryPhone = firstNotBlank(
        infocenter,
        infocenterculture,
        infocenterleports,
        infocenterlodging,
        infocentershopping,
        infocenterfood,
        infocentertourcourse,
    ),
    fee = firstNotBlank(usefee, usefeeleports),
    petAllowed = firstNotBlank(chkpet, chkpetculture, chkpetleports, chkpetshopping),
    creditCardAccepted = firstNotBlank(
        chkcreditcard,
        chkcreditcardculture,
        chkcreditcardleports,
        chkcreditcardshopping,
        chkcreditcardfood,
    ),
).takeUnless { it.isEmpty() }

/** detailInfo2 item 목록을 이용안내 항목으로 옮긴다. 항목명·내용 중 하나라도 비면 제외한다. */
fun List<TourApiDetailInfoResponse.Item>.toInfoItems(): List<SpotInfoItem> = mapNotNull { it.toSpotInfoItemOrNull() }

/** detailInfo2 item → 도메인 [SpotInfoItem]. 항목명·내용이 모두 있어야 한다. */
fun TourApiDetailInfoResponse.Item.toSpotInfoItemOrNull(): SpotInfoItem? {
    val name = infoname?.takeIf { it.isNotBlank() } ?: return null
    val text = infotext?.takeIf { it.isNotBlank() } ?: return null
    return SpotInfoItem(name = name, text = text)
}

private fun firstNotBlank(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }

/** 도메인이 https URL을 요구하므로 매핑 단계에서 정규화한다. */
internal fun String?.toHttps(): String = orEmpty().replaceFirst("http://", "https://")
