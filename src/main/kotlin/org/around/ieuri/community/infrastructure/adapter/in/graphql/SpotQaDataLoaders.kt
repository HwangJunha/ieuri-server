package org.around.ieuri.community.infrastructure.adapter.`in`.graphql

import com.netflix.graphql.dgs.DgsDataLoader
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.community.application.port.`in`.query.SpotQaQueryUseCase
import org.around.ieuri.community.application.query.SpotAnswerReplyView
import org.dataloader.MappedBatchLoader
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * 장소 질문/답변의 배치 로더(SPEC-0026).
 *
 * 지연 필드는 요청당 과다 조회를 막고 DataLoader는 요청 **안**의 N+1을 막는 것이라,
 * 둘은 다른 문제이며 둘 다 필요하다.
 *
 * **비로그인을 허용한다** — 질문 목록은 인증 없이 읽히므로(AC-1.4) `requireMemberId`가
 * 아니라 `currentMemberId`를 쓴다. 0은 "차단 관계도 소유 관계도 없는 호출자"를 뜻한다.
 */

@DgsDataLoader(name = SpotAnswerReplyDataLoader.NAME)
@Component
class SpotAnswerReplyDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
    private val currentMemberProvider: CurrentMemberProvider,
) : MappedBatchLoader<Long, List<SpotAnswerReplyView>> {

    override fun load(answerIds: Set<Long>): CompletionStage<Map<Long, List<SpotAnswerReplyView>>> {
        val memberId = currentMemberProvider.currentMemberId() ?: ANONYMOUS
        val replies = spotQaQueryUseCase.answerReplies(answerIds.toList(), memberId)
        // 답글이 없는 답변은 결과에 키가 없다 — 빈 목록으로 채워야 리졸버가 null을 만나지 않는다.
        return CompletableFuture.completedFuture(answerIds.associateWith { replies[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReply"
    }
}

@DgsDataLoader(name = SpotQuestionPhotoDataLoader.NAME)
@Component
class SpotQuestionPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(questionIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.questionPhotoKeys(questionIds.toList())
        return CompletableFuture.completedFuture(questionIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotQuestionPhoto"
    }
}

@DgsDataLoader(name = SpotAnswerPhotoDataLoader.NAME)
@Component
class SpotAnswerPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(answerIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.answerPhotoKeys(answerIds.toList())
        return CompletableFuture.completedFuture(answerIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerPhoto"
    }
}

@DgsDataLoader(name = SpotAnswerReplyPhotoDataLoader.NAME)
@Component
class SpotAnswerReplyPhotoDataLoader(
    private val spotQaQueryUseCase: SpotQaQueryUseCase,
) : MappedBatchLoader<Long, List<String>> {

    override fun load(replyIds: Set<Long>): CompletionStage<Map<Long, List<String>>> {
        val photoKeys = spotQaQueryUseCase.replyPhotoKeys(replyIds.toList())
        return CompletableFuture.completedFuture(replyIds.associateWith { photoKeys[it].orEmpty() })
    }

    companion object {
        const val NAME = "spotAnswerReplyPhoto"
    }
}

/** 비로그인 호출자. 차단 집합도 소유 관계도 없어 어떤 회원 id와도 겹치지 않는 값이다. */
internal const val ANONYMOUS = 0L
