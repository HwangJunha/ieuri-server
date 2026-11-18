package org.around.ieuri.common.graphql

import graphql.ExecutionResult
import graphql.execution.AbortExecutionException
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationContext
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimpleInstrumentationContext
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters
import graphql.language.Field
import graphql.language.FragmentDefinition
import graphql.language.FragmentSpread
import graphql.language.InlineFragment
import graphql.language.SelectionSet
import org.around.ieuri.common.security.CurrentMemberProvider
import org.around.ieuri.common.security.EmailVerificationGate
import org.around.ieuri.common.security.EmailVerificationStatusPort
import org.around.ieuri.common.security.SecurityGraphQLError
import org.springframework.stereotype.Component

/**
 * 이메일 미인증 회원의 오퍼레이션을 차단한다(SPEC-0024 R9). HTTP·WebSocket이 이 한 지점을 공유한다.
 * 판정은 [EmailVerificationGate]가 한다.
 */
@Component
class EmailVerificationGateInstrumentation(
    private val currentMemberProvider: CurrentMemberProvider,
    private val emailVerificationStatus: EmailVerificationStatusPort,
) : Instrumentation {

    override fun beginExecuteOperation(
        parameters: InstrumentationExecuteOperationParameters,
        state: InstrumentationState?,
    ): InstrumentationContext<ExecutionResult> {
        // 주체가 없는 요청은 판정하지 않는다 — 비로그인은 기존 인증 처리의 몫이다.
        val memberId = currentMemberProvider.currentMemberId()
        if (memberId != null) {
            val context = parameters.executionContext
            val rootFields = rootFieldNames(
                context.operationDefinition.selectionSet,
                context.fragmentsByName,
            )
            if (!EmailVerificationGate.isAllowed(rootFields, emailVerificationStatus.isEmailVerified(memberId))) {
                // `AbortExecutionException`이어야 한다 — DataFetcher 바깥이라 graphql-java가 errors[]로
                // 바꿔 주는 예외는 이 타입뿐이다. 다른 예외는 execute() 밖으로 새어 나간다.
                throw AbortExecutionException(
                    listOf(
                        SecurityGraphQLError(
                            message = "이메일 인증이 필요합니다. 메일함의 인증 코드를 확인해 주세요.",
                            code = EMAIL_NOT_VERIFIED,
                        ),
                    ),
                )
            }
        }
        return SimpleInstrumentationContext.noOp()
    }

    /**
     * 루트 셀렉션의 필드 이름(별칭 아님). 프래그먼트를 펼친다 — 펼치지 않으면 `...Fragment`만 보낸 요청이 통과한다.
     */
    private fun rootFieldNames(
        selectionSet: SelectionSet,
        fragments: Map<String, FragmentDefinition>,
    ): List<String> = selectionSet.selections.flatMap { selection ->
        when (selection) {
            is Field -> listOf(selection.name)

            is InlineFragment -> rootFieldNames(selection.selectionSet, fragments)

            is FragmentSpread ->
                fragments[selection.name]?.let { rootFieldNames(it.selectionSet, fragments) }.orEmpty()

            else -> emptyList()
        }
    }

    private companion object {
        const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    }
}
