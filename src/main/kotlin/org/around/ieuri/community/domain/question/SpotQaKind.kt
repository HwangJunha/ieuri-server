package org.around.ieuri.community.domain.question

/** 장소 Q&A 글의 종류. 각 엔티티가 자기 종류를 고정으로 든다 — 사진 주인 구분 등 종류로 갈리는 곳이 이 값을 쓴다. */
enum class SpotQaKind { QUESTION, ANSWER, REPLY }
