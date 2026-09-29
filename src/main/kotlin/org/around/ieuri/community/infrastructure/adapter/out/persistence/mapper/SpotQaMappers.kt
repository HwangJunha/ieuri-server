package org.around.ieuri.community.infrastructure.adapter.out.persistence.mapper

import org.around.ieuri.community.domain.aggregate.SpotAnswer
import org.around.ieuri.community.domain.aggregate.SpotAnswerReply
import org.around.ieuri.community.domain.aggregate.SpotQuestion
import org.around.ieuri.community.domain.vo.PhotoObjectKey
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQuestionEntity

/** 질문 엔티티를 도메인으로(SPEC-0026). 별도 표의 사진은 호출자가 읽어 넘긴다. */
fun SpotQuestionEntity.toDomain(photoKeys: List<PhotoObjectKey>): SpotQuestion = SpotQuestion.restore(
    id = requireNotNull(id) { "적재된 질문에는 id가 있어야 한다." },
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    photoKeys = photoKeys,
    resolved = resolved,
    answerCount = answerCount,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 질문에는 createdAt이 있어야 한다." },
)

/** 도메인 질문을 새 엔티티로. 사진은 id가 정해진 뒤 따로 적재한다. */
fun SpotQuestion.toNewEntity(): SpotQuestionEntity = SpotQuestionEntity(
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    resolved = resolved,
    answerCount = answerCount,
    hidden = hidden,
    deletedAt = deletedAt,
)

fun SpotAnswerEntity.toDomain(photoKeys: List<PhotoObjectKey>): SpotAnswer = SpotAnswer.restore(
    id = requireNotNull(id) { "적재된 답변에는 id가 있어야 한다." },
    questionId = questionId,
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    photoKeys = photoKeys,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 답변에는 createdAt이 있어야 한다." },
)

fun SpotAnswer.toNewEntity(): SpotAnswerEntity = SpotAnswerEntity(
    questionId = questionId,
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    hidden = hidden,
    deletedAt = deletedAt,
)

fun SpotAnswerReplyEntity.toDomain(photoKeys: List<PhotoObjectKey>): SpotAnswerReply = SpotAnswerReply.restore(
    id = requireNotNull(id) { "적재된 답글에는 id가 있어야 한다." },
    answerId = answerId,
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    photoKeys = photoKeys,
    hidden = hidden,
    deletedAt = deletedAt,
    createdAt = requireNotNull(createdAt) { "적재된 답글에는 createdAt이 있어야 한다." },
)

fun SpotAnswerReply.toNewEntity(): SpotAnswerReplyEntity = SpotAnswerReplyEntity(
    answerId = answerId,
    catalogId = catalogId,
    authorId = authorId,
    body = body,
    hidden = hidden,
    deletedAt = deletedAt,
)

/** 배열 순서를 position에 싣는다(표시 순서). */
fun List<PhotoObjectKey>.toPhotoEntities(
    ownerType: SpotQaPhotoOwnerType,
    ownerId: Long,
): List<SpotQaPhotoEntity> = mapIndexed { index, key ->
    SpotQaPhotoEntity(
        ownerType = ownerType,
        ownerId = ownerId,
        objectKey = key.value,
        position = index.toShort(),
    )
}

/** 파싱되지 않는 사진 키는 버리지 않고 실패시킨다(데이터 손상). */
fun SpotQaPhotoEntity.toPhotoKey(): PhotoObjectKey = requireNotNull(PhotoObjectKey.parse(objectKey)) {
    "적재된 사진 키($objectKey)의 형식이 올바르지 않다."
}
