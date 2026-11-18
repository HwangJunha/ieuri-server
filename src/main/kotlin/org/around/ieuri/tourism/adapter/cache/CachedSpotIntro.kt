package org.around.ieuri.tourism.adapter.cache

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.around.ieuri.tourism.domain.spot.SpotIntro
@JsonIgnoreProperties(ignoreUnknown = true)
data class CachedSpotIntro(
    val businessHours: String? = null,
    val closedDays: String? = null,
    val parking: String? = null,
    val inquiryPhone: String? = null,
    val fee: String? = null,
    val petAllowed: String? = null,
    val creditCardAccepted: String? = null,
) {
    fun toDomain(): SpotIntro = SpotIntro(
        businessHours = businessHours,
        closedDays = closedDays,
        parking = parking,
        inquiryPhone = inquiryPhone,
        fee = fee,
        petAllowed = petAllowed,
        creditCardAccepted = creditCardAccepted,
    )

    companion object {
        fun from(intro: SpotIntro): CachedSpotIntro = CachedSpotIntro(
            businessHours = intro.businessHours,
            closedDays = intro.closedDays,
            parking = intro.parking,
            inquiryPhone = intro.inquiryPhone,
            fee = intro.fee,
            petAllowed = intro.petAllowed,
            creditCardAccepted = intro.creditCardAccepted,
        )
    }
}
