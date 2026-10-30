package org.around.ieuri.auth.domain.shared

/** 후보 하나에 참·거짓을 답하는 규칙(Evans·Fowler의 Specification). `and`·`or`·`not`으로 조합한다. */
fun interface Specification<T> {
    fun isSatisfiedBy(candidate: T): Boolean

    infix fun and(other: Specification<T>): Specification<T> = Specification { isSatisfiedBy(it) && other.isSatisfiedBy(it) }

    infix fun or(other: Specification<T>): Specification<T> = Specification { isSatisfiedBy(it) || other.isSatisfiedBy(it) }

    operator fun not(): Specification<T> = Specification { !isSatisfiedBy(it) }
}
