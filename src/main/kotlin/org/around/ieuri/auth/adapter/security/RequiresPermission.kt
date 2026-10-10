package org.around.ieuri.auth.adapter.security
/** 리졸버 메서드에 필요한 권한을 선언한다. 예: `@RequiresPermission(Permission.MEMBER_READ)`. */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequiresPermission(val permission: String)
