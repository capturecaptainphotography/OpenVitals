package tech.mmarca.openvitals.features.workoutplans

import androidx.health.connect.client.records.ExerciseSegment
import tech.mmarca.openvitals.domain.workout.ProgrammeExercise

/**
 * How a programme's exercise is written as a Health Connect segment.
 *
 * Health Connect has no constant for push-ups, calf raises, side planks or the dead bug, so
 * those ride on `OTHER_WORKOUT` with a description, the way the builder's push-up preset does.
 */
data class ProgrammeSegment(val segmentType: Int, val description: String? = null)

fun ProgrammeExercise.segment(): ProgrammeSegment = when (this) {
    ProgrammeExercise.SQUAT -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT)
    ProgrammeExercise.LUNGE -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE)
    ProgrammeExercise.GLUTE_BRIDGE -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST)
    ProgrammeExercise.CALF_RAISE -> ProgrammeSegment(OtherWorkout, "Calf raise")
    ProgrammeExercise.PUSH_UP -> ProgrammeSegment(OtherWorkout, "Push-ups")
    ProgrammeExercise.DUMBBELL_ROW -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_DUMBBELL_ROW)
    ProgrammeExercise.SHOULDER_PRESS -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS)
    ProgrammeExercise.BICEP_CURL -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL)
    ProgrammeExercise.PLANK -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK)
    ProgrammeExercise.SIDE_PLANK -> ProgrammeSegment(OtherWorkout, "Side plank")
    ProgrammeExercise.DEAD_BUG -> ProgrammeSegment(OtherWorkout, "Dead bug")
    ProgrammeExercise.CRUNCH -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH)
    ProgrammeExercise.BURPEE -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BURPEE)
    ProgrammeExercise.MOUNTAIN_CLIMBER ->
        ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_MOUNTAIN_CLIMBER)
    ProgrammeExercise.JUMPING_JACK -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_JUMPING_JACK)
    ProgrammeExercise.STRETCH -> ProgrammeSegment(ExerciseSegment.EXERCISE_SEGMENT_TYPE_STRETCHING)
}

/**
 * The programme exercise a builder step is, or null for a movement the programme does not use.
 * A step that rides on `OTHER_WORKOUT` matches only when its description names the movement.
 */
fun programmeExerciseFor(segmentType: Int, description: String?): ProgrammeExercise? =
    ProgrammeExercise.entries.firstOrNull { exercise ->
        val segment = exercise.segment()
        segment.segmentType == segmentType &&
            (
                segment.description == null ||
                    segment.description.equals(description?.trim(), ignoreCase = true)
                )
    }

private val OtherWorkout = ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT
