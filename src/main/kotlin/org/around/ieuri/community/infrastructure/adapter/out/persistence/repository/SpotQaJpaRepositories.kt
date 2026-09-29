package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

interface SpotQuestionJpaRepository : JpaRepository<SpotQuestionEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotQuestionEntity?

    // deleteById는 답변·답글을 CASCADE로 지우지만 사진 행은 남긴다 — 어댑터가 먼저 지운다.
}

interface SpotAnswerJpaRepository : JpaRepository<SpotAnswerEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotAnswerEntity?

    /** 질문에 달린 답변 전부(사진 정리 대상 수집용). */
    fun findByQuestionId(questionId: Long): List<SpotAnswerEntity>
}

interface SpotAnswerReplyJpaRepository : JpaRepository<SpotAnswerReplyEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotAnswerReplyEntity?

    /** 답변에 달린 답글 전부(사진 정리 대상 수집용). */
    fun findByAnswerId(answerId: Long): List<SpotAnswerReplyEntity>

    fun findByAnswerIdIn(answerIds: Collection<Long>): List<SpotAnswerReplyEntity>
}

/** 질문·답변·답글의 사진. position 순서가 표시 순서다. */
interface SpotQaPhotoJpaRepository : JpaRepository<SpotQaPhotoEntity, Long> {
    fun findByOwnerTypeAndOwnerIdOrderByPositionAsc(
        ownerType: SpotQaPhotoOwnerType,
        ownerId: Long,
    ): List<SpotQaPhotoEntity>

    fun deleteByOwnerTypeAndOwnerId(ownerType: SpotQaPhotoOwnerType, ownerId: Long): Long

    fun deleteByOwnerTypeAndOwnerIdIn(
        ownerType: SpotQaPhotoOwnerType,
        ownerIds: Collection<Long>,
    ): Long
}
