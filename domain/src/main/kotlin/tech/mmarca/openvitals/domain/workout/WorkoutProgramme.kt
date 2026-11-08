package tech.mmarca.openvitals.domain.workout

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * A year-long progressive training programme, built on the phone from a level and a start
 * date alone: no network, no account and no randomness, so the same two inputs always give
 * the same plan.
 *
 * The shape:
 *
 * - four-week blocks; the first three weeks add work, the fourth is a deload at about sixty
 *   percent of the volume;
 * - the year runs ADAPTATION (weeks 1-4), BUILD (5-24), PEAK (25-48) and TAPER (49-52);
 * - sessions per week grow twice, at the start of BUILD and of PEAK, up to the level's cap;
 * - sets climb one per block to six, repetitions and holds climb every block to their caps,
 *   and rest falls as the blocks go by and as the level gets harder, with a floor;
 * - every week carries a minute cap, so an advanced year cannot quietly become a full-time job.
 *
 * This file is data and arithmetic only. Health Connect segment types, string resources and
 * screens live in `:app`.
 */
enum class ProgrammeLevel(
    /** Sessions a week in the first block. */
    val startingSessions: Int,
    /** The most sessions a week ever holds. */
    val maxSessions: Int,
    val startingSets: Int,
    val startingRepetitions: Int,
    val maxRepetitions: Int,
    val startingHoldSeconds: Int,
    val maxHoldSeconds: Int,
    val startingRestSeconds: Int,
    /** The most minutes a whole week may ask for. */
    val weeklyMinuteCap: Int,
) {
    BEGINNER(2, 4, 2, 8, 20, 15, 90, 90, 180),
    INTERMEDIATE(3, 5, 3, 10, 25, 20, 120, 75, 240),
    ADVANCED(4, 5, 3, 12, 30, 25, 150, 60, 300),
}

/** Where a week sits in the year. The phase says what the block is for, not how hard it is. */
enum class ProgrammePhase(val firstWeek: Int, val lastWeek: Int) {
    ADAPTATION(1, 4),
    BUILD(5, 24),
    PEAK(25, 48),
    TAPER(49, 52),
    ;

    companion object {
        fun of(week: Int): ProgrammePhase = entries.first { week in it.firstWeek..it.lastWeek }
    }
}

/** What a step asks for: a number of repetitions, or a hold in seconds. */
sealed interface ProgrammeTarget {
    data class Repetitions(val count: Int) : ProgrammeTarget

    data class Hold(val seconds: Int) : ProgrammeTarget
}

enum class ProgrammeFocus {
    LOWER_BODY,
    UPPER_BODY,
    CORE,
    CONDITIONING,
    MOBILITY,
}

/**
 * The movements a programme draws from. [timed] steps are held for a number of seconds; the
 * rest count repetitions. The app maps each one to a Health Connect segment.
 */
enum class ProgrammeExercise(val focus: ProgrammeFocus, val timed: Boolean = false) {
    SQUAT(ProgrammeFocus.LOWER_BODY),
    LUNGE(ProgrammeFocus.LOWER_BODY),
    GLUTE_BRIDGE(ProgrammeFocus.LOWER_BODY),
    CALF_RAISE(ProgrammeFocus.LOWER_BODY),
    PUSH_UP(ProgrammeFocus.UPPER_BODY),
    DUMBBELL_ROW(ProgrammeFocus.UPPER_BODY),
    SHOULDER_PRESS(ProgrammeFocus.UPPER_BODY),
    BICEP_CURL(ProgrammeFocus.UPPER_BODY),
    PLANK(ProgrammeFocus.CORE, timed = true),
    SIDE_PLANK(ProgrammeFocus.CORE, timed = true),
    DEAD_BUG(ProgrammeFocus.CORE),
    CRUNCH(ProgrammeFocus.CORE),
    BURPEE(ProgrammeFocus.CONDITIONING),
    MOUNTAIN_CLIMBER(ProgrammeFocus.CONDITIONING),
    JUMPING_JACK(ProgrammeFocus.CONDITIONING, timed = true),
    STRETCH(ProgrammeFocus.MOBILITY, timed = true),
}

/** One exercise in a session: how many sets, what each set asks for, and the rest between sets. */
data class ProgrammeStep(
    val exercise: ProgrammeExercise,
    val sets: Int,
    val target: ProgrammeTarget,
    val restSeconds: Int,
)

data class ProgrammeSession(
    val day: DayOfWeek,
    val focus: ProgrammeFocus,
    val steps: List<ProgrammeStep>,
    val estimatedMinutes: Int,
)

data class ProgrammeWeek(
    val number: Int,
    val phase: ProgrammePhase,
    val isDeload: Boolean,
    val sessions: List<ProgrammeSession>,
) {
    val estimatedMinutes: Int get() = sessions.sumOf { it.estimatedMinutes }
}

data class WorkoutProgramme(
    val level: ProgrammeLevel,
    val startDate: LocalDate,
    val weeks: List<ProgrammeWeek>,
) {
    /** Weeks turn over on Monday, whichever day the programme started. */
    val firstMonday: LocalDate = startDate.minusDays((startDate.dayOfWeek.value - 1).toLong())

    /**
     * The 1-based week [date] falls in, 0 before the programme and past its last week after it.
     * Floored, so the day before the first Monday lands in week 0 rather than week 1.
     */
    fun weekNumberOn(date: LocalDate): Int =
        (Math.floorDiv(ChronoUnit.DAYS.between(firstMonday, date), DaysPerWeek) + 1).toInt()

    fun weekOf(date: LocalDate): ProgrammeWeek? = weeks.getOrNull(weekNumberOn(date) - 1)

    /** The session on [date]'s weekday of its week, or null on a rest day. */
    fun sessionOn(date: LocalDate): ProgrammeSession? =
        weekOf(date)?.sessions?.firstOrNull { it.day == date.dayOfWeek }

    /** How far through the year [date] is: 0 before the start, 1 at the end. What a ring animates. */
    fun progressOn(date: LocalDate): Float {
        val total = weeks.size * DaysPerWeek.toInt()
        if (total <= 0) return 0f
        val elapsed = ChronoUnit.DAYS.between(firstMonday, date).toFloat()
        return (elapsed / total.toFloat()).coerceIn(0f, 1f)
    }
}

object WorkoutProgrammeGenerator {
    const val DefaultWeeks = 52

    fun generate(
        level: ProgrammeLevel,
        startDate: LocalDate,
        weeks: Int = DefaultWeeks,
    ): WorkoutProgramme {
        require(weeks > 0) { "a programme needs at least one week" }
        return WorkoutProgramme(level, startDate, (1..weeks).map { week(level, it) })
    }

    private fun week(level: ProgrammeLevel, number: Int): ProgrammeWeek {
        val block = (number - 1) / WeeksPerBlock
        val isDeload = number % WeeksPerBlock == 0
        val focuses = focusesFor(sessionsFor(level, number))
        val days = daysFor(focuses.size)
        val sessions = focuses.mapIndexed { slot, focus ->
            session(level, block, isDeload, days[slot], focus)
        }
        return ProgrammeWeek(
            number = number,
            phase = ProgrammePhase.of(number),
            isDeload = isDeload,
            sessions = sessions,
        )
    }

    private fun sessionsFor(level: ProgrammeLevel, week: Int): Int {
        val added = when {
            week >= PeakSessionsFromWeek -> 2
            week >= BuildSessionsFromWeek -> 1
            else -> 0
        }
        return (level.startingSessions + added).coerceAtMost(level.maxSessions)
    }

    private fun focusesFor(count: Int): List<ProgrammeFocus> = FocusRotation.take(count)

    private fun daysFor(count: Int): List<DayOfWeek> =
        TrainingDays[count] ?: TrainingDays.getValue(TrainingDays.keys.max())

    private fun session(
        level: ProgrammeLevel,
        block: Int,
        isDeload: Boolean,
        day: DayOfWeek,
        focus: ProgrammeFocus,
    ): ProgrammeSession {
        val sets = setsFor(level, block, isDeload)
        val rest = restFor(level, block, isDeload)
        val steps = exercisesFor(focus).take(StepsPerSession).map { exercise ->
            ProgrammeStep(
                exercise = exercise,
                sets = sets,
                target = targetFor(level, exercise, block, isDeload),
                restSeconds = rest,
            )
        }
        return ProgrammeSession(
            day = day,
            focus = focus,
            steps = steps,
            estimatedMinutes = estimatedMinutes(steps),
        )
    }

    private fun setsFor(level: ProgrammeLevel, block: Int, isDeload: Boolean): Int {
        val sets = (level.startingSets + block).coerceAtMost(MaxSets)
        return if (isDeload) (sets - 1).coerceAtLeast(MinSets) else sets
    }

    private fun targetFor(
        level: ProgrammeLevel,
        exercise: ProgrammeExercise,
        block: Int,
        isDeload: Boolean,
    ): ProgrammeTarget = if (exercise.timed) {
        ProgrammeTarget.Hold(holdSecondsFor(level, block, isDeload))
    } else {
        ProgrammeTarget.Repetitions(repetitionsFor(level, block, isDeload))
    }

    private fun repetitionsFor(level: ProgrammeLevel, block: Int, isDeload: Boolean): Int {
        val reps = (level.startingRepetitions + block * RepetitionStep).coerceAtMost(level.maxRepetitions)
        return if (isDeload) {
            (reps * DeloadFraction).roundToInt().coerceAtLeast(MinRepetitions)
        } else {
            reps
        }
    }

    private fun holdSecondsFor(level: ProgrammeLevel, block: Int, isDeload: Boolean): Int {
        val seconds = (level.startingHoldSeconds + block * HoldStepSeconds).coerceAtMost(level.maxHoldSeconds)
        return if (isDeload) {
            (seconds * DeloadFraction).roundToInt().coerceAtLeast(MinHoldSeconds)
        } else {
            seconds
        }
    }

    private fun restFor(level: ProgrammeLevel, block: Int, isDeload: Boolean): Int {
        val rest = (level.startingRestSeconds - block * RestStepSeconds).coerceAtLeast(MinRestSeconds)
        return if (isDeload) (rest + DeloadExtraRestSeconds).coerceAtMost(MaxRestSeconds) else rest
    }

    private fun estimatedMinutes(steps: List<ProgrammeStep>): Int {
        val seconds = steps.sumOf { step ->
            val work = when (val target = step.target) {
                is ProgrammeTarget.Repetitions -> target.count * SecondsPerRepetition
                is ProgrammeTarget.Hold -> target.seconds
            }
            step.sets.toLong() * work + (step.sets - 1).toLong() * step.restSeconds
        }
        return (seconds / SecondsPerMinute).roundToInt().coerceAtLeast(1)
    }

    private fun exercisesFor(focus: ProgrammeFocus): List<ProgrammeExercise> = when (focus) {
        ProgrammeFocus.LOWER_BODY -> LowerBody
        ProgrammeFocus.UPPER_BODY -> UpperBody
        ProgrammeFocus.CORE -> Core
        ProgrammeFocus.CONDITIONING -> Conditioning
        ProgrammeFocus.MOBILITY -> Mobility
    }

    private val FocusRotation = listOf(
        ProgrammeFocus.LOWER_BODY,
        ProgrammeFocus.UPPER_BODY,
        ProgrammeFocus.CORE,
        ProgrammeFocus.CONDITIONING,
        ProgrammeFocus.MOBILITY,
    )

    /** Training days by sessions a week, spaced so no two hard sessions sit back to back. */
    private val TrainingDays: Map<Int, List<DayOfWeek>> = mapOf(
        2 to listOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        3 to listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        4 to listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
        5 to listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
        ),
    )

    private val LowerBody = listOf(
        ProgrammeExercise.SQUAT,
        ProgrammeExercise.LUNGE,
        ProgrammeExercise.GLUTE_BRIDGE,
        ProgrammeExercise.CALF_RAISE,
    )
    private val UpperBody = listOf(
        ProgrammeExercise.PUSH_UP,
        ProgrammeExercise.DUMBBELL_ROW,
        ProgrammeExercise.SHOULDER_PRESS,
        ProgrammeExercise.BICEP_CURL,
    )
    private val Core = listOf(
        ProgrammeExercise.PLANK,
        ProgrammeExercise.DEAD_BUG,
        ProgrammeExercise.CRUNCH,
        ProgrammeExercise.SIDE_PLANK,
    )
    private val Conditioning = listOf(
        ProgrammeExercise.BURPEE,
        ProgrammeExercise.MOUNTAIN_CLIMBER,
        ProgrammeExercise.JUMPING_JACK,
        ProgrammeExercise.STRETCH,
    )
    private val Mobility = listOf(
        ProgrammeExercise.STRETCH,
        ProgrammeExercise.GLUTE_BRIDGE,
        ProgrammeExercise.DEAD_BUG,
        ProgrammeExercise.SQUAT,
    )
}

private const val DaysPerWeek = 7L
private const val WeeksPerBlock = 4
private const val MaxSets = 6
private const val MinSets = 2
private const val RepetitionStep = 2
private const val HoldStepSeconds = 5
private const val RestStepSeconds = 5
private const val MinRestSeconds = 30
private const val MaxRestSeconds = 120
private const val DeloadExtraRestSeconds = 15
private const val DeloadFraction = 0.6
private const val MinRepetitions = 6
private const val MinHoldSeconds = 10
private const val SecondsPerRepetition = 3
private const val SecondsPerMinute = 60.0
private const val StepsPerSession = 3
private const val BuildSessionsFromWeek = 13
private const val PeakSessionsFromWeek = 25
