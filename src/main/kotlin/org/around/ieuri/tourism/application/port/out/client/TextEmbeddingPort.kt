package org.around.ieuri.tourism.application.port.out.client

/**
 * 텍스트 임베딩 제공자 포트(SPEC-0035). 입력 순서대로 벡터를 돌려준다.
 * - 실패는 예외로 올린다 — 재시도·건너뛰기는 호출자의 일이다.
 */
interface TextEmbeddingPort {
    fun embed(texts: List<String>, model: String): List<FloatArray>
}
