package org.around.ieuri.auth.infrastructure.security

/**
 * 인바운드 어댑터(GraphQL 리졸버) 메서드에 필요한 권한을 선언한다.
 * RequiresPermissionAspect가 메서드 실행 전에 Authorizer로 권한을 강제한다 —
 * 인가 판정을 리졸버 본문에서 분리해 선언적으로 만든다.
 *
 * 권한 코드는 Permission의 const 상수를 넘겨 컴파일 타임에 오타를 막는다.
 * 예: `@RequiresPermission(Permission.MEMBER_READ)`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPermission(val permission: String)
