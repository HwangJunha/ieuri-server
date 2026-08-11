package org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.mapper

import org.around.ieuri.tourism.application.command.SyncPetFriendlyCommand
import org.around.ieuri.tourism.infrastructure.adapter.out.client.tourapi.TourApiPetTourResponse

/**
 * detailPetTour2 item → 적재 [SyncPetFriendlyCommand](SPEC-0015 · V48 · POL-033).
 *
 * **contentid가 없으면 null로 드랍한다** — PK가 될 값이라 이것 없이는 적재할 수 없다. 나머지
 * 필드는 전부 결손을 허용한다.
 *
 * **빈 문자열은 null이 된다.** 업스트림이 결손을 `""`로 보내는 필드가 있어, 그대로 넣으면
 * "값이 없음"과 "빈 값"이 DB에서 갈린다(POL-043).
 *
 * 순수 함수다 — I/O도 로깅도 하지 않는다.
 */
fun TourApiPetTourResponse.Item.toSyncPetFriendlyCommandOrNull(): SyncPetFriendlyCommand? =
    SyncPetFriendlyCommand(
        contentId = contentid?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
        accompanyType = acmpyTypeCd.blankToNull(),
        accompanyScope = acmpyPsblCpam.blankToNull(),
        requiredMatters = acmpyNeedMtr.blankToNull(),
        etcInfo = etcAcmpyInfo.blankToNull(),
    )

/**
 * 공백뿐인 값은 null. **trim한 결과를 돌려주지 않는다** — etcAcmpyInfo는 줄바꿈이 의미를 갖는
 * 여러 줄 안내문이라 앞뒤 공백까지 원문 그대로 보존한다.
 */
private fun String?.blankToNull(): String? = this?.takeIf { it.isNotBlank() }
