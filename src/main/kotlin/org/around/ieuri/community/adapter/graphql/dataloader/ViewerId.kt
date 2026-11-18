package org.around.ieuri.community.adapter.graphql.dataloader

import org.dataloader.BatchLoaderEnvironment

/**
 * 뷰어 신원을 keyContext로 싣기 위한 래퍼.
 * - 맨 `Long`으로 바꾸면 `filterIsInstance`가 키 id와 구별하지 못한다.
 */
@JvmInline
value class ViewerId(val value: Long)

/**
 * 배치에 실려 온 뷰어를 꺼낸다(SPEC-0032 R27). 한 배치의 뷰어는 하나다.
 * - 없으면 비로그인으로 폴백하지 않고 실패한다.
 */
internal fun BatchLoaderEnvironment.requireViewer(loaderName: String): Long = keyContexts.values
    .filterIsInstance<ViewerId>()
    .firstOrNull()
    ?.value
    ?: error("${loaderName}은 keyContext로 뷰어를 받아야 한다 — 리졸버가 load(key, ViewerId(...))로 부른다.")

/** 비로그인 호출자. 어떤 회원 id와도 겹치지 않는다. 장소 질문/답변 로더가 쓴다(SPEC-0026 AC-1.4). */
internal const val ANONYMOUS = 0L
