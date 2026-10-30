package org.around.ieuri.auth.adapter.security.authorization
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.around.ieuri.auth.adapter.security.authorization.Authorizer
import org.around.ieuri.auth.adapter.security.authorization.RequiresPermission
import org.around.ieuri.auth.adapter.security.authorization.RequiresPermissionAspect
import org.around.ieuri.auth.domain.member.Permission
import org.around.ieuri.common.security.ForbiddenException
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** @RequiresPermission 애스펙트가 실행 전에 Authorizer.require를 호출하고 거부 시 실행을 막는지 본다(AspectJProxyFactory, 컨텍스트 없음). */
class RequiresPermissionAspectTest {

    private val authorizer = mockk<Authorizer>(relaxed = true)

    /** 프록시 대상. CGLIB가 가로채도록 open. guarded는 보호되고, unguarded는 애노테이션이 없다. */
    open class Target {
        @RequiresPermission(Permission.MEMBER_READ)
        open fun guarded(): String = "executed"

        open fun unguarded(): String = "executed"
    }

    private fun proxied(): Target {
        val factory = AspectJProxyFactory(Target())
        factory.addAspect(RequiresPermissionAspect(authorizer))
        return factory.getProxy()
    }

    @Test
    fun `애노테이션이 붙은 메서드는 실행 전 해당 권한을 요구한다`() {
        val result = proxied().guarded()

        assertEquals("executed", result)
        verify(exactly = 1) { authorizer.require(Permission.MEMBER_READ) }
    }

    @Test
    fun `애노테이션이 없는 메서드는 권한을 요구하지 않는다`() {
        proxied().unguarded()

        verify(exactly = 0) { authorizer.require(any()) }
    }

    @Test
    fun `권한이 없으면(Authorizer 거부) 메서드 실행이 막힌다`() {
        every { authorizer.require(Permission.MEMBER_READ) } throws ForbiddenException("권한이 필요합니다: ${Permission.MEMBER_READ}")

        assertFailsWith<ForbiddenException> { proxied().guarded() }
    }
}
