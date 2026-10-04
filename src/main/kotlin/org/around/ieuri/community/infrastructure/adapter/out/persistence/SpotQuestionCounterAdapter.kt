package org.around.ieuri.community.infrastructure.adapter.out.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import org.around.ieuri.community.application.port.out.persistence.SpotQuestionCounterPort
import org.around.ieuri.community.infrastructure.adapter.out.persistence.entity.QSpotQuestionEntity
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** 질문의 답변 수 증감. 원자적 UPDATE로 처리한다([PostCounterAdapter]와 같다). */
@Component
class SpotQuestionCounterAdapter(
    private val queryFactory: JPAQueryFactory,
) : SpotQuestionCounterPort {

    private val question = QSpotQuestionEntity.spotQuestionEntity

    @Transactional
    override fun increaseAnswerCount(questionId: Long) {
        queryFactory
            .update(question)
            .set(question.answerCount, question.answerCount.add(1))
            .where(question.id.eq(questionId), question.deletedAt.isNull)
            .execute()
    }

    /** 0 아래로 내려가지 않는다 — 예외 없이 0에 머문다. */
    @Transactional
    override fun decreaseAnswerCount(questionId: Long) {
        queryFactory
            .update(question)
            .set(question.answerCount, question.answerCount.subtract(1))
            .where(
                question.id.eq(questionId),
                question.deletedAt.isNull,
                question.answerCount.gt(0),
            )
            .execute()
    }
}
