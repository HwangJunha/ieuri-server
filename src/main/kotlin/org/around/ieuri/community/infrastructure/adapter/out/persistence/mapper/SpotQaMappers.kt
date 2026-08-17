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

/**
 * 장소 질문/답변의 엔티티↔도메인 변환(SPEC-0026). 형태만 옮기며 업무 규칙도 I/O도 없다.
 *
 * 사진은 별도 테이블이라 **호출자가 읽어 넘긴다** — 엔티티에 연관으로 두면 조회마다 따라
 * 붙는다(SpotQaEntities 주석 참고).
 */

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

/** 도메인 질문을 새 엔티티로. id·시각은 DB가 채우고 사진은 id가 정해진 뒤 따로 적재한다. */
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

fun SpotAnswerReplyEntity.toDomain(photoKeys: List<PhotoObjectKey>): SpotAnswerReply =
    SpotAnswerReply.restore(
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

/**
 * 배열 순서가 표시 순서다 — position에 그대로 싣는다.
 *
 * 셋이 같은 표를 쓰므로 [ownerType]을 함께 받는다. 소유자 종류를 매퍼가 추론하지 않는
 * 이유는 도메인 타입에 그 정보가 없기 때문이다 — 호출자가 무엇을 저장하는지 알고 있다.
 */
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

/**
 * 적재된 사진 키. **파싱되지 않으면 조용히 버리지 않고 실패시킨다** — 적재 시점에 형식을
 * 통과한 값이므로 파싱 실패는 데이터 손상이며, 넘기면 사진 한 장이 이유 없이 사라진다.
 */
fun SpotQaPhotoEntity.toPhotoKey(): PhotoObjectKey =
    requireNotNull(PhotoObjectKey.parse(objectKey)) {
        "적재된 사진 키(${objectKey})의 형식이 올바르지 않다."
    }
