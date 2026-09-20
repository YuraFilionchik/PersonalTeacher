package com.example.personallangmaster.core.srs

enum class ReviewGrade { AGAIN, HARD, GOOD, EASY }

enum class SrsState { NEW, LEARNING, REVIEW, MATURE, SUSPENDED }

data class SrsCard(
    val ease: Double,
    val intervalDays: Int,
    val repetitions: Int,
    val lapses: Int,
    val state: SrsState,
    val dueAtEpochDay: Long,
    val lastReviewedEpochDay: Long?,
)
