package org.around.ieuri.community.infrastructure.adapter.out.persistence.repository

import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotAnswerReplyEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoEntity
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQaPhotoOwnerType
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.SpotQuestionEntity
import org.springframework.data.jpa.repository.JpaRepository

/**
 * 장소 질문/답변 저장소(SPEC-0026). 파생 쿼리만 쓴다 — 복잡한 조회는 QueryDSL 어댑터가 맡는다.
 *
 * **탈퇴 회원 콘텐츠의 소프트 삭제는 여기 없다** — 벌크 UPDATE라 파생 쿼리로 표현할 수 없고,
 * `@Query` 문자열은 이 저장소가 쓰지 않는다. QueryDSL 어댑터가 맡는다
 * ([org.around.ieuri.community.infrastructure.adapter.out.persistence.MemberContentSoftDeleteAdapter]).
 */

interface SpotQuestionJpaRepository : JpaRepository<SpotQuestionEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotQuestionEntity?

    // 물리 삭제는 상속받은 deleteById를 쓴다. 딸린 답변·답글은 DB의 ON DELETE CASCADE가
    // 따라 지우지만 **사진 행은 따라오지 않는다** — FK가 없어 어댑터가 직접 지운다.
}

interface SpotAnswerJpaRepository : JpaRepository<SpotAnswerEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotAnswerEntity?

    /**
     * 질문에 달린 답변 전부. **사진 정리 대상을 모으는 용도다** — 답변 행 자체는 CASCADE가
     * 지우지만 사진은 FK가 없어 id를 알아야 지울 수 있다.
     */
    fun findByQuestionId(questionId: Long): List<SpotAnswerEntity>
}

interface SpotAnswerReplyJpaRepository : JpaRepository<SpotAnswerReplyEntity, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): SpotAnswerReplyEntity?

    /** 답변에 달린 답글 전부. 답변과 같은 이유로 사진 정리에 필요하다. */
    fun findByAnswerId(answerId: Long): List<SpotAnswerReplyEntity>

    fun findByAnswerIdIn(answerIds: Collection<Long>): List<SpotAnswerReplyEntity>
}

/** 질문·답변·답글의 사진. position 순서가 표시 순서다. */
interface SpotQaPhotoJpaRepository : JpaRepository<SpotQaPhotoEntity, Long> {
    fun findByOwnerTypeAndOwnerIdOrderByPositionAsc(
        ownerType: SpotQaPhotoOwnerType,
        ownerId: Long,
    ): List<SpotQaPhotoEntity>

    /** 부모가 지워질 때 사진 행을 함께 지운다 — FK가 없어 DB가 대신해 주지 않는다. */
    fun deleteByOwnerTypeAndOwnerId(ownerType: SpotQaPhotoOwnerType, ownerId: Long): Long

    fun deleteByOwnerTypeAndOwnerIdIn(
        ownerType: SpotQaPhotoOwnerType,
        ownerIds: Collection<Long>,
    ): Long
}
