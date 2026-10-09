package org.around.ieuri.tourism.domain.region

/** 대표 위치를 다시 정한 결과([District.relocateRepresentative]). */
sealed interface RepresentativeRelocation {
    /** 이번에 고른 원본으로 대표 위치를 둔다 — 이미 있던 시군구도 다시 쓴다. */
    data class Assign(val choice: RepresentativeLocation.Choice) : RepresentativeRelocation

    /** 문턱에 못 미쳐 있던 대표 위치를 비운다. */
    data object Clear : RepresentativeRelocation

    /** 고를 것도 비울 것도 없다. */
    data object Unchanged : RepresentativeRelocation
}
