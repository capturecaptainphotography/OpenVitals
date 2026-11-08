package tech.mmarca.openvitals.domain.workout

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The programme's arithmetic: fifty-two weeks, deloads, growth and caps. */
class WorkoutProgrammeTest {

    /** A Wednesday, so the first Monday is the 5th and the first week does not start on the start day. */
    private val start = LocalDate.of(2026, 1, 7)
    private val firstMonday = LocalDate.of(2026, 1, 5)

    private fun programme(level: ProgrammeLevel) = WorkoutProgrammeGenerator.generate(level, start)

    private fun volume(week: ProgrammeWeek): Int = week.sessions.sumOf { session ->
        session.steps.sumOf { step ->
            val units = when (val target = step.target) {
                is ProgrammeTarget.Repetitions -> target.count
                is ProgrammeTarget.Hold -> target.seconds
            }
            step.sets * units
        }
    }

    @Test
    fun `the year holds fifty-two numbered weeks and four phases`() {
        val plan = programme(ProgrammeLevel.INTERMEDIATE)

        assertEquals(52, plan.weeks.size)
        assertEquals((1..52).toList(), plan.weeks.map { it.number })
        assertEquals(ProgrammePhase.ADAPTATION, plan.weeks.first().phase)
        assertEquals(ProgrammePhase.TAPER, plan.weeks.last().phase)
        assertEquals(ProgrammePhase.BUILD, ProgrammePhase.of(5))
        assertEquals(ProgrammePhase.PEAK, ProgrammePhase.of(25))
    }

    @Test
    fun `every fourth week is a deload and holds less work than the week before`() {
        ProgrammeLevel.entries.forEach { level ->
            val plan = programme(level)
            val deloads = plan.weeks.filter { it.isDeload }

            assertEquals(13, deloads.size)
            deloads.forEach { week ->
                val previous = plan.weeks.first { it.number == week.number - 1 }
                assertTrue(
                    "week ${week.number} of $level is not lighter than ${previous.number}",
                    volume(week) < volume(previous),
                )
            }
        }
    }

    @Test
    fun `work grows between deloads and resumes after them`() {
        ProgrammeLevel.entries.forEach { level ->
            val weeks = programme(level).weeks
            weeks.zipWithNext().forEach { (before, after) ->
                if (!after.isDeload && !before.isDeload) {
                    assertTrue(
                        "week ${after.number} of $level drops below ${before.number}",
                        volume(after) >= volume(before),
                    )
                }
                if (before.isDeload && !after.isDeload) {
                    assertTrue(
                        "week ${after.number} of $level does not resume past the deload",
                        volume(after) > volume(before),
                    )
                }
            }
        }
    }

    @Test
    fun `a harder level asks for more in the last week than an easier one`() {
        val beginner = volume(programme(ProgrammeLevel.BEGINNER).weeks.last { !it.isDeload })
        val intermediate = volume(programme(ProgrammeLevel.INTERMEDIATE).weeks.last { !it.isDeload })
        val advanced = volume(programme(ProgrammeLevel.ADVANCED).weeks.last { !it.isDeload })

        assertTrue(beginner < intermediate)
        assertTrue(intermediate < advanced)
    }

    @Test
    fun `sessions per week grow with the level and through the year`() {
        ProgrammeLevel.entries.forEach { level ->
            val plan = programme(level)
            val first = plan.weeks.first().sessions.size
            val afterBuild = plan.weeks.first { it.number == 13 }.sessions.size
            val afterPeak = plan.weeks.first { it.number == 25 }.sessions.size

            assertEquals(level.startingSessions, first)
            assertTrue(afterBuild >= first)
            assertTrue(afterPeak >= afterBuild)
            assertTrue(afterPeak <= level.maxSessions)
            assertTrue(plan.weeks.all { it.sessions.size <= level.maxSessions })
        }
    }

    @Test
    fun `no week asks for more than its minute cap`() {
        ProgrammeLevel.entries.forEach { level ->
            programme(level).weeks.forEach { week ->
                assertTrue(
                    "week ${week.number} of $level asks for ${week.estimatedMinutes} minutes " +
                        "over the ${level.weeklyMinuteCap} cap",
                    week.estimatedMinutes <= level.weeklyMinuteCap,
                )
            }
        }
    }

    @Test
    fun `sets repetitions holds and rests stay inside their limits`() {
        ProgrammeLevel.entries.forEach { level ->
            programme(level).weeks.forEach { week ->
                week.sessions.forEach { session ->
                    assertEquals(3, session.steps.size)
                    assertTrue(session.estimatedMinutes > 0)
                    session.steps.forEach { step ->
                        assertTrue(step.sets in 2..6)
                        assertTrue(step.restSeconds in 30..120)
                        val units = when (val target = step.target) {
                            is ProgrammeTarget.Repetitions -> target.count
                            is ProgrammeTarget.Hold -> target.seconds
                        }
                        assertTrue("${step.exercise} asks for $units", units > 0)
                        assertEquals(step.exercise.timed, step.target is ProgrammeTarget.Hold)
                    }
                }
            }
        }
    }

    @Test
    fun `the same level and start always give the same programme`() {
        assertEquals(
            programme(ProgrammeLevel.ADVANCED),
            WorkoutProgrammeGenerator.generate(ProgrammeLevel.ADVANCED, start),
        )
        assertFalse(programme(ProgrammeLevel.BEGINNER) == programme(ProgrammeLevel.ADVANCED))
    }

    @Test
    fun `dates map onto weeks anchored on the Monday`() {
        val plan = programme(ProgrammeLevel.BEGINNER)

        assertNull(plan.weekOf(firstMonday.minusDays(1)))
        assertEquals(1, plan.weekOf(firstMonday)?.number)
        assertEquals(1, plan.weekOf(firstMonday.plusDays(6))?.number)
        assertEquals(2, plan.weekOf(firstMonday.plusDays(7))?.number)
        assertEquals(52, plan.weekOf(firstMonday.plusWeeks(51))?.number)
        assertNull(plan.weekOf(firstMonday.plusWeeks(52)))
    }

    @Test
    fun `a session is found on its day and nothing on a rest day`() {
        val plan = programme(ProgrammeLevel.BEGINNER)
        val monday = firstMonday
        val tuesday = firstMonday.plusDays(1)

        assertEquals(monday.dayOfWeek, plan.sessionOn(monday)?.day)
        assertNull(plan.sessionOn(tuesday))
    }

    @Test
    fun `progress runs from zero to one across the year`() {
        val plan = programme(ProgrammeLevel.INTERMEDIATE)

        assertEquals(0f, plan.progressOn(start.minusDays(1)), 0f)
        assertEquals(0f, plan.progressOn(firstMonday), 0f)
        assertEquals(1f, plan.progressOn(firstMonday.plusWeeks(52)), 0f)
        val middle = plan.progressOn(firstMonday.plusWeeks(26))
        assertTrue(middle > 0.4f && middle < 0.6f)
    }

    @Test
    fun `a programme needs at least one week`() {
        val failure = runCatching {
            WorkoutProgrammeGenerator.generate(ProgrammeLevel.BEGINNER, start, weeks = 0)
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }
}
