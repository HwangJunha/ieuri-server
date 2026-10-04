package org.around.ieuri.community.adapter.persistence

import org.around.ieuri.community.adapter.persistence.table.SpotQaPhotoTable
import org.around.ieuri.community.domain.shared.AttachedPhotos
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.deleteWhere

// 질문·답변·답글 사진 행. 주인은 다형 참조라 FK가 없다 — 주인을 지우기 전에 사진을 먼저 지운다.

/** `owner_type` 값(CHECK 제약과 같다). */
internal enum class SpotQaPhotoOwner { QUESTION, ANSWER, REPLY }

internal fun insertSpotQaPhotos(owner: SpotQaPhotoOwner, ownerId: Long, photos: AttachedPhotos) {
    SpotQaPhotoTable.batchInsert(photos.keys.withIndex(), shouldReturnGeneratedValues = false) { (index, key) ->
        this[SpotQaPhotoTable.ownerType] = owner.name
        this[SpotQaPhotoTable.ownerId] = ownerId
        this[SpotQaPhotoTable.objectKey] = key.value
        this[SpotQaPhotoTable.position] = index.toShort()
    }
}

internal fun deleteSpotQaPhotos(owner: SpotQaPhotoOwner, ownerId: Long) {
    SpotQaPhotoTable.deleteWhere { (SpotQaPhotoTable.ownerType eq owner.name) and (SpotQaPhotoTable.ownerId eq ownerId) }
}

/** [ownerIds]는 주인 id 한 열을 고르는 서브쿼리다. */
internal fun deleteSpotQaPhotos(owner: SpotQaPhotoOwner, ownerIds: Query) {
    SpotQaPhotoTable.deleteWhere { (SpotQaPhotoTable.ownerType eq owner.name) and (SpotQaPhotoTable.ownerId inSubQuery ownerIds) }
}
