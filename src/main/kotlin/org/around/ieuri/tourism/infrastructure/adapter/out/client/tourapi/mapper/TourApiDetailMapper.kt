package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper

import org.around.ieuri.tourism.domain.aggregate.SpotDetail
import org.around.ieuri.tourism.domain.vo.CopyrightType
import org.around.ieuri.tourism.domain.vo.SpotCategory
import org.around.ieuri.tourism.domain.vo.SpotImage
import org.around.ieuri.tourism.domain.vo.SpotInfoItem
import org.around.ieuri.tourism.domain.vo.SpotIntro
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiDetailCommonResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiDetailImageResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiDetailInfoResponse
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiDetailIntroResponse

/**
 * detailCommon2 item을 도메인 [SpotDetail] 본문으로 옮긴다.
 *
 * [category]를 인자로 받는다 — contentTypeId 해석은 언어별 코드표에 의존하므로 클라이언트가 정한다.
 */
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
 * detailIntro2 item을 도메인 [SpotIntro]로 옮긴다. 표시할 값이 하나도 없으면 null이다.
 *
 * 카테고리별로 흩어진 필드를 공통 필드로 정규화한다 — 응답이 자기 카테고리 필드만 채우므로
 * 후보를 순서대로 훑어 처음 값이 있는 것을 쓴다.
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

/** detailInfo2 item을 도메인 [SpotInfoItem]으로 옮긴다. 항목명·내용이 모두 있어야 의미가 있다. */
fun TourApiDetailInfoResponse.Item.toSpotInfoItemOrNull(): SpotInfoItem? {
    val name = infoname?.takeIf { it.isNotBlank() } ?: return null
    val text = infotext?.takeIf { it.isNotBlank() } ?: return null
    return SpotInfoItem(name = name, text = text)
}

private fun firstNotBlank(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }

/** 도메인이 https URL을 요구하므로 매핑 단계에서 정규화한다. */
internal fun String?.toHttps(): String = orEmpty().replaceFirst("http://", "https://")
