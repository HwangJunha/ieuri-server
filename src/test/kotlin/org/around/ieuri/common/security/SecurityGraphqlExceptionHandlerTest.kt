package org.around.ieuri.common.security

import graphql.Scalars
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.ExecutionStepInfo
import graphql.execution.ResultPath
import graphql.language.Field
import graphql.schema.DataFetchingEnvironment
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CompletionException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 인증·권한 실패가 **코드와 함께** 최상위 errors[]로 나가는지(POL-016).
 *
 * 핵심은 **감싸인 예외를 푼다**는 것이다. DGS는 리졸버를 리플렉션으로 호출하므로 리졸버가
 * 던진 예외가 `InvocationTargetException`에 담겨 도착한다. 껍데기를 벗기지 않으면 `is`
 * 검사가 빗나가 정제 핸들러까지 흘러가고, 클라이언트는 코드 없는 `INTERNAL_ERROR`를 받는다 —
 * 자동 토큰 갱신은 `UNAUTHENTICATED`를 보고 돌기 때문에 그때 갱신도 재로그인도 되지 않는다.
 * (실측: conversations 조회가 미인증 상태에서 INTERNAL_ERROR로 나갔다.)
 */
class SecurityGraphqlExceptionHandlerTest {

    private val handler = SecurityGraphqlExceptionHandler()

    /**
     * `build()`가 환경에서 path·location을 읽으므로 최소 스텁이 필요하다. 핸들러의 분기는
     * 예외 타입만 보므로 이 둘의 값 자체는 판정에 영향을 주지 않는다.
     */
    private fun codeOf(exception: Throwable): String {
        val environment = mockk<DataFetchingEnvironment>(relaxed = true) {
            every { executionStepInfo } returns
                ExecutionStepInfo.newExecutionStepInfo()
                    .type(Scalars.GraphQLString)
                    .path(ResultPath.rootPath().segment("conversations"))
                    .build()
            every { field } returns Field.newField("conversations").build()
        }
        val params = DataFetcherExceptionHandlerParameters
            .newExceptionParameters()
            .dataFetchingEnvironment(environment)
            .exception(exception)
            .build()
        val result = handler.handleException(params).join()
        val error = result.errors.single()
        return error.extensions["code"] as String
    }

    @Test
    fun `인증 실패는 UNAUTHENTICATED로 나간다`() {
        assertEquals("UNAUTHENTICATED", codeOf(UnauthorizedException("인증이 필요합니다.")))
    }

    @Test
    fun `권한 실패는 FORBIDDEN으로 나간다`() {
        assertEquals("FORBIDDEN", codeOf(ForbiddenException("권한이 없습니다.")))
    }

    /** 리플렉션 호출이 만드는 실제 형태다 — 이 테스트가 회귀를 막는다. */
    @Test
    fun `InvocationTargetException에 감싸여도 UNAUTHENTICATED로 나간다`() {
        val wrapped = InvocationTargetException(UnauthorizedException("인증이 필요합니다."))

        assertEquals("UNAUTHENTICATED", codeOf(wrapped))
    }

    /** 비동기 경로(CompletableFuture)는 CompletionException으로 한 겹 더 감싼다. */
    @Test
    fun `여러 겹으로 감싸여도 끝까지 풀어낸다`() {
        val wrapped = CompletionException(
            InvocationTargetException(UnauthorizedException("인증이 필요합니다.")),
        )

        assertEquals("UNAUTHENTICATED", codeOf(wrapped))
    }

    @Test
    fun `감싸인 권한 실패도 FORBIDDEN으로 나간다`() {
        assertEquals(
            "FORBIDDEN",
            codeOf(InvocationTargetException(ForbiddenException("권한이 없습니다."))),
        )
    }

    /**
     * 모르는 예외는 정제 핸들러로 넘어가 고정 메시지·고정 코드가 된다(POL-013) —
     * 예외 FQCN이나 원문이 클라이언트로 새지 않아야 한다.
     */
    @Test
    fun `모르는 예외는 INTERNAL_ERROR로 정제된다`() {
        assertEquals("INTERNAL_ERROR", codeOf(IllegalStateException("테이블 제약 위반 상세")))
    }

    /** 감싸인 것이 모르는 예외라면 그것도 정제 대상이다. */
    @Test
    fun `감싸인 모르는 예외도 INTERNAL_ERROR로 정제된다`() {
        assertEquals(
            "INTERNAL_ERROR",
            codeOf(InvocationTargetException(IllegalStateException("내부 상세"))),
        )
    }

    /** cause가 자기 자신이면 무한 루프에 빠지지 않아야 한다. */
    @Test
    fun `자기 자신을 cause로 갖는 예외에서도 멈춘다`() {
        val looping = object : RuntimeException("순환") {
            override val cause: Throwable get() = this
        }

        assertEquals("INTERNAL_ERROR", codeOf(looping))
    }
}
