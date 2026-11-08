package tech.mmarca.openvitals.features.workoutplans

import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import tech.mmarca.openvitals.R
import tech.mmarca.openvitals.domain.workout.ProgrammeExercise

/** The clip that ships for [exercise]. Every exercise has one; the enum keeps the two in step. */
fun programmeExerciseAnimation(exercise: ProgrammeExercise): Int = when (exercise) {
    ProgrammeExercise.SQUAT -> R.drawable.exercise_squat
    ProgrammeExercise.LUNGE -> R.drawable.exercise_lunge
    ProgrammeExercise.GLUTE_BRIDGE -> R.drawable.exercise_glute_bridge
    ProgrammeExercise.CALF_RAISE -> R.drawable.exercise_calf_raise
    ProgrammeExercise.PUSH_UP -> R.drawable.exercise_push_up
    ProgrammeExercise.DUMBBELL_ROW -> R.drawable.exercise_dumbbell_row
    ProgrammeExercise.SHOULDER_PRESS -> R.drawable.exercise_shoulder_press
    ProgrammeExercise.BICEP_CURL -> R.drawable.exercise_bicep_curl
    ProgrammeExercise.PLANK -> R.drawable.exercise_plank
    ProgrammeExercise.SIDE_PLANK -> R.drawable.exercise_side_plank
    ProgrammeExercise.DEAD_BUG -> R.drawable.exercise_dead_bug
    ProgrammeExercise.CRUNCH -> R.drawable.exercise_crunch
    ProgrammeExercise.BURPEE -> R.drawable.exercise_burpee
    ProgrammeExercise.MOUNTAIN_CLIMBER -> R.drawable.exercise_mountain_climber
    ProgrammeExercise.JUMPING_JACK -> R.drawable.exercise_jumping_jack
    ProgrammeExercise.STRETCH -> R.drawable.exercise_stretch
}

/**
 * The looping demonstration of [exercise], so a movement can be recognised before it is run.
 *
 * The clips are GIFs in `drawable-nodpi`, drawn by `scripts/generate-exercise-animations.py`.
 * Android 9 and newer animate them; older versions show the first frame. The first frame is
 * the middle of the movement, so a still reads as the exercise rather than as a rest pose.
 * [contentDescription] carries the localised exercise name, so the clip is never the only
 * way to tell what a step is.
 */
@Composable
internal fun ExerciseAnimation(
    exercise: ProgrammeExercise,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = DefaultExerciseAnimationSize,
) {
    val clip = programmeExerciseAnimation(exercise)
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(clip)
                this.contentDescription = contentDescription
            }
        },
        modifier = modifier.size(size),
    )
}

internal val DefaultExerciseAnimationSize = 48.dp
