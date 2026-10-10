package org.around.ieuri.common.persistence

import org.jetbrains.exposed.v1.core.CustomFunction
import org.jetbrains.exposed.v1.core.Expression
import org.jetbrains.exposed.v1.core.ExpressionWithColumnType
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.intLiteral
import org.jetbrains.exposed.v1.core.stringLiteral

// 문자열 SQL 함수. 이름은 SQL 함수 이름을 그대로 따른다.

/** `md5(text)` — 32자 16진 문자열. */
fun md5(text: Expression<String>): ExpressionWithColumnType<String> = CustomFunction("md5", TextColumnType(), text)

/** `left(text, n)` — 앞 [length]자. */
fun Expression<String>.leftChars(length: Int): ExpressionWithColumnType<String> = CustomFunction("left", TextColumnType(), this, intLiteral(length))

/** `COALESCE(text, '')` — null이면 빈 문자열. */
fun Expression<String?>.orEmpty(): ExpressionWithColumnType<String> = CustomFunction("COALESCE", TextColumnType(), this, stringLiteral(""))
