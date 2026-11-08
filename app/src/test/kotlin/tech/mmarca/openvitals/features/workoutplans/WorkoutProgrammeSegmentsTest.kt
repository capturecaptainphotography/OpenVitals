package tech.mmarca.openvitals.features.workoutplans

import androidx.health.connect.client.records.ExerciseSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import tech.mmarca.openvitals.domain.workout.ProgrammeExercise

class WorkoutProgrammeSegmentsTest {

    @Test
    fun `every programme exercise maps to a segment and to a clip`() {
        assertEquals(16, ProgrammeExercise.entries.size)
        ProgrammeExercise.entries.forEach { exercise ->
            assertNotNull("${exercise.name} has no segment", exercise.segment())
            assertNotNull("${exercise.name} has no clip", programmeExerciseAnimation(exercise))
        }
    }

    @Test
    fun `only the movements Health Connect cannot name carry a description`() {
        val described = ProgrammeExercise.entries.filter { it.segment().description != null }

        assertEquals(
            setOf(
                ProgrammeExercise.CALF_RAISE,
                ProgrammeExercise.PUSH_UP,
                ProgrammeExercise.SIDE_PLANK,
                ProgrammeExercise.DEAD_BUG,
            ),
            described.toSet(),
        )
        assertNull(ProgrammeExercise.SQUAT.segment().description)
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            ProgrammeExercise.PUSH_UP.segment().segmentType,
        )
    }

    @Test
    fun `named movements keep their own constant`() {
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
            ProgrammeExercise.PLANK.segment().segmentType,
        )
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
            ProgrammeExercise.LUNGE.segment().segmentType,
        )
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING,
            ProgrammeExercise.STRETCH.segment().segmentType,
        )
    }

    @Test
    fun `a step finds its programme exercise by segment and description`() {
        assertEquals(
            ProgrammeExercise.SQUAT,
            programmeExerciseFor(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, description = ""),
        )
        assertEquals(
            ProgrammeExercise.PUSH_UP,
            programmeExerciseFor(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
                description = " push-ups ",
            ),
        )
        assertNull(
            programmeExerciseFor(
                ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
                description = "Kettlebell clean",
            ),
        )
        assertNull(programmeExerciseFor(ExerciseSegment.EXERCISE_SEGMENT_TYPE_REST, description = null))
        assertNull(programmeExerciseFor(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SWIMMING_POOL, "Swimming"))
    }
}
