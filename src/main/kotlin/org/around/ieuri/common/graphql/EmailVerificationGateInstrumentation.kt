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
 * 이메일 미인증 회원의 오퍼레이션을 차단한다(SPEC-0024 R9).
 *
 * **리졸버마다 심지 않고 한 지점에 두는 것이 의도다** — 허용 목록 7개를 뺀 모든 오퍼레이션에
 * 걸리는 횡단 관심사라 리졸버에 흩으면 새 오퍼레이션이 추가될 때마다 빠뜨린다.
 *
 * `Instrumentation`인 이유는 두 가지다. `GraphQlSource`에 얹히므로 **HTTP·WebSocket이 한 빈을
 * 공유하고**, `beginExecuteOperation` 시점에는 문서가 이미 파싱돼 있어 루트 필드 이름을 다시
 * 파싱하지 않고 읽을 수 있다. `WebGraphQlInterceptor`는 원문 문자열만 주므로 같은 판정을 하려면
 * 문서를 한 번 더 파싱해야 한다.
 *
 * 판정 자체는 [EmailVerificationGate]가 하고 이 클래스는 값을 모아 넘긴 뒤 예외를 던진다.
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
        // 주체가 없는 요청은 판정하지 않는다(R9-5) — 비로그인은 기존 인증 처리의 몫이고,
        // 여기서 걸면 클라이언트가 인증 화면 대신 이메일 인증 화면으로 보낸다.
        val memberId = currentMemberProvider.currentMemberId()
        if (memberId != null) {
            val context = parameters.executionContext
            val rootFields = rootFieldNames(
                context.operationDefinition.selectionSet,
                context.fragmentsByName,
            )
            if (!EmailVerificationGate.isAllowed(rootFields, emailVerificationStatus.isEmailVerified(memberId))) {
                // **`AbortExecutionException`이어야 한다.** 이 지점은 DataFetcher 바깥이라
                // `DataFetcherExceptionHandler`가 보지 못하고, graphql-java가 errors[]로
                // 바꿔 주는 예외는 이 타입 하나뿐이다(GraphQL.java의 유일한 catch).
                // 평범한 RuntimeException은 execute() 밖으로 그대로 새어 나가 클라이언트가
                // 코드를 읽지 못한다 — `GraphQLError` 구현만으로도 부족하다.
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
     * 루트 셀렉션의 **필드 이름**. 별칭이 아니라 이름을 쓴다 — 허용 목록이 필드 이름 기준이라
     * `alias: me`가 판정을 비껴가면 안 된다.
     *
     * **프래그먼트를 펼치는 것이 핵심이다.** `query { ...MyFields }`는 적법한데, 필드만 세면
     * 루트가 0개로 보여 그대로 통과한다 — 조용히 열리는 실패라 반드시 다룬다.
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
        /** 코드 문자열은 SPEC-0024 이전과 같다. 위치만 payload에서 최상위 오류로 옮겼다. */
        const val EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED"
    }
}
