#!/usr/bin/env python3
"""Builds the looping exercise clips that sit beside a workout-plan step.

The clips are drawn here, not licensed from anywhere: a mid-grey stick figure on a
transparent background, twelve frames, one loop. Mid-grey is deliberate — the app has a
light and a dark theme, and one ink colour has to be readable on both.

Every exercise in `domain/workout/WorkoutProgramme.kt` must get exactly one clip; the
script reads that enum and fails when they drift apart.

    python3 scripts/generate-exercise-animations.py

Writes `app/src/main/res/drawable-nodpi/exercise_<name>.gif`, and a contact sheet at
`/tmp/exercise-animations.png` for a quick look.
"""

from __future__ import annotations

import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

REPO = Path(__file__).resolve().parent.parent
PROGRAMME = REPO / "domain/src/main/kotlin/tech/mmarca/openvitals/domain/workout/WorkoutProgramme.kt"
OUTPUT = REPO / "app/src/main/res/drawable-nodpi"
PREVIEW = Path("/tmp/exercise-animations.png")

SIZE = 240
SUPERSAMPLE = 2
FRAMES = 12
FRAME_MS = 90
WIDTH = 7
GROUND = 212
INK = (138, 143, 152, 255)
FLOOR = (200, 205, 212, 255)
HEAD_RADIUS = 12

BONES = (
    ("head", "neck"),
    ("neck", "hip"),
    ("hip", "l_knee"),
    ("l_knee", "l_foot"),
    ("hip", "r_knee"),
    ("r_knee", "r_foot"),
    ("neck", "l_elbow"),
    ("l_elbow", "l_hand"),
    ("neck", "r_elbow"),
    ("r_elbow", "r_hand"),
)


def dip(phase: float) -> float:
    """One smooth down-and-up per loop, 0 at the ends and 1 in the middle."""
    return (1 - math.cos(2 * math.pi * phase)) / 2


def sway(phase: float) -> float:
    """A full left-and-right swing per loop, -1 to 1."""
    return math.sin(2 * math.pi * phase)


def mix(first: dict, second: dict, amount: float) -> dict:
    return {
        name: (
            first[name][0] + (second[name][0] - first[name][0]) * amount,
            first[name][1] + (second[name][1] - first[name][1]) * amount,
        )
        for name in first
    }


def squat(phase: float) -> dict:
    down = dip(phase)
    return {
        "head": (116, 50 + 52 * down),
        "neck": (118, 76 + 52 * down),
        "hip": (120, 108 + 52 * down),
        "l_knee": (160, 148 + 28 * down),
        "l_foot": (142, GROUND),
        "r_knee": (104, 148 + 28 * down),
        "r_foot": (96, GROUND),
        "l_elbow": (142, 94 + 52 * down),
        "l_hand": (176, 84 + 52 * down),
        "r_elbow": (96, 96 + 52 * down),
        "r_hand": (64, 108 + 52 * down),
    }


def lunge(phase: float) -> dict:
    down = dip(phase)
    return {
        "head": (116, 52 + 40 * down),
        "neck": (118, 78 + 40 * down),
        "hip": (120, 110 + 40 * down),
        "l_knee": (158, 150 + 20 * down),
        "l_foot": (170, GROUND),
        "r_knee": (112, 162 + 20 * down),
        "r_foot": (74, GROUND),
        "l_elbow": (140, 96 + 40 * down),
        "l_hand": (150, 140 + 40 * down),
        "r_elbow": (96, 96 + 40 * down),
        "r_hand": (86, 140 + 40 * down),
    }


def glute_bridge(phase: float) -> dict:
    lift = dip(phase)
    return {
        "head": (46, 190),
        "neck": (68, 190),
        "hip": (120, 188 - 42 * lift),
        "l_knee": (168, 150),
        "l_foot": (182, GROUND),
        "r_knee": (160, 152),
        "r_foot": (172, GROUND),
        "l_elbow": (80, 206),
        "l_hand": (100, 210),
        "r_elbow": (84, 202),
        "r_hand": (104, 206),
    }


def calf_raise(phase: float) -> dict:
    lift = 16 * dip(phase)
    return {
        "head": (118, 50 - lift),
        "neck": (120, 76 - lift),
        "hip": (122, 108 - lift),
        "l_knee": (120, 158 - lift),
        "l_foot": (130, GROUND),
        "r_knee": (128, 158 - lift),
        "r_foot": (138, GROUND),
        "l_elbow": (146, 94 - lift),
        "l_hand": (158, 118 - lift),
        "r_elbow": (100, 94 - lift),
        "r_hand": (88, 124 - lift),
    }


def push_up(phase: float) -> dict:
    down = dip(phase)
    return {
        "head": (198, 142 + 16 * down),
        "neck": (180, 150 + 16 * down),
        "hip": (118, 176 + 10 * down),
        "l_knee": (84, 192 + 6 * down),
        "l_foot": (52, GROUND),
        "r_knee": (88, 196 + 6 * down),
        "r_foot": (58, GROUND),
        "l_elbow": (162, 180 - 8 * down),
        "l_hand": (150, GROUND),
        "r_elbow": (170, 178 - 8 * down),
        "r_hand": (158, GROUND),
    }


def plank(phase: float) -> dict:
    down = dip(phase)
    # A hold pose still drifts a little sideways: two identical frames would be merged by
    # the GIF encoder, and a merged frame makes the loop stutter.
    drift = 2 * sway(phase)
    return {
        "head": (194 + drift, 118 + 8 * down),
        "neck": (174 + drift, 126 + 7 * down),
        "hip": (112 + drift, 150 + 6 * down),
        "l_knee": (78, 176),
        "l_foot": (48, GROUND),
        "r_knee": (82, 178),
        "r_foot": (54, GROUND),
        "l_elbow": (168, 168),
        "l_hand": (160, GROUND),
        "r_elbow": (176, 166),
        "r_hand": (168, GROUND),
    }


def side_plank(phase: float) -> dict:
    down = dip(phase)
    drift = 2 * sway(phase)
    return {
        "head": (162 + drift, 102 + 7 * down),
        "neck": (150 + drift, 120 + 7 * down),
        "hip": (96 + drift, 158 + 7 * down),
        "l_knee": (58, 196),
        "l_foot": (36, GROUND),
        "r_knee": (64, 192),
        "r_foot": (42, GROUND),
        "l_elbow": (148, 168),
        "l_hand": (146, GROUND),
        "r_elbow": (172, 96 + 7 * down),
        "r_hand": (200, 64 + 7 * down),
    }


def dead_bug(phase: float) -> dict:
    swing = sway(phase)
    reach = max(swing, 0.0)
    other = max(-swing, 0.0)
    return {
        "head": (46, 196),
        "neck": (70, 194),
        "hip": (130, 196),
        "l_knee": (160, 168 - 16 * reach),
        "l_foot": (176, 200 - 26 * reach),
        "r_knee": (150, 176),
        "r_foot": (160, 208),
        "l_elbow": (88, 166),
        "l_hand": (96, 136),
        "r_elbow": (84, 170 - 8 * other),
        "r_hand": (76, 146 - 18 * other),
    }


def crunch(phase: float) -> dict:
    curl = dip(phase)
    return {
        "head": (60 - 8 * curl, 196 - 28 * curl),
        "neck": (84 - 6 * curl, 194 - 18 * curl),
        "hip": (140, 196),
        "l_knee": (168, 168),
        "l_foot": (182, GROUND),
        "r_knee": (160, 172),
        "r_foot": (174, GROUND),
        "l_elbow": (74, 176 - 14 * curl),
        "l_hand": (66, 156 - 26 * curl),
        "r_elbow": (90, 178 - 12 * curl),
        "r_hand": (84, 158 - 24 * curl),
    }


def burpee(phase: float) -> dict:
    standing = {
        "head": (116, 50),
        "neck": (118, 76),
        "hip": (120, 110),
        "l_knee": (128, 160),
        "l_foot": (126, GROUND),
        "r_knee": (112, 160),
        "r_foot": (110, GROUND),
        "l_elbow": (140, 96),
        "l_hand": (142, 138),
        "r_elbow": (96, 96),
        "r_hand": (94, 138),
    }
    crouch = squat(0.5)
    low = plank(0.0)
    if phase < 0.25:
        return mix(standing, crouch, phase / 0.25)
    if phase < 0.5:
        return mix(crouch, low, (phase - 0.25) / 0.25)
    if phase < 0.75:
        return mix(low, crouch, (phase - 0.5) / 0.25)
    return mix(crouch, standing, (phase - 0.75) / 0.25)


def mountain_climber(phase: float) -> dict:
    swing = sway(phase)
    pose = plank(0.0)
    drive = max(swing, 0.0)
    back = max(-swing, 0.0)
    pose["l_knee"] = (78 + 62 * drive, 176 - 34 * drive)
    pose["l_foot"] = (48 + 66 * drive, GROUND - 40 * drive)
    pose["r_knee"] = (82 + 62 * back, 178 - 34 * back)
    pose["r_foot"] = (54 + 66 * back, GROUND - 40 * back)
    return pose


def jumping_jack(phase: float) -> dict:
    spread = dip(phase)
    return {
        "head": (120, 66),
        "neck": (120, 96),
        "hip": (120, 128),
        "l_knee": (120 - 26 * spread, 170),
        "l_foot": (120 - 42 * spread, GROUND),
        "r_knee": (120 + 26 * spread, 170),
        "r_foot": (120 + 42 * spread, GROUND),
        "l_elbow": (120 - 30 * spread, 120 - 40 * spread),
        "l_hand": (120 - 48 * spread, 98 - 74 * spread),
        "r_elbow": (120 + 30 * spread, 120 - 40 * spread),
        "r_hand": (120 + 48 * spread, 98 - 74 * spread),
    }


def bicep_curl(phase: float) -> dict:
    curl = dip(phase)
    return {
        "head": (120, 68),
        "neck": (120, 98),
        "hip": (120, 132),
        "l_knee": (108, 172),
        "l_foot": (104, GROUND),
        "r_knee": (132, 172),
        "r_foot": (136, GROUND),
        "l_elbow": (96, 124),
        "l_hand": (96 + 20 * curl, 124 - 34 * curl),
        "r_elbow": (144, 124),
        "r_hand": (144 - 20 * curl, 124 - 34 * curl),
    }


def dumbbell_row(phase: float) -> dict:
    pull = dip(phase)
    return {
        "head": (160, 96),
        "neck": (146, 104),
        "hip": (96, 130),
        "l_knee": (112, 170),
        "l_foot": (106, GROUND),
        "r_knee": (104, 168),
        "r_foot": (98, GROUND),
        "l_elbow": (140, 132 - 12 * pull),
        "l_hand": (140 + 8 * pull, 164 - 40 * pull),
        "r_elbow": (152, 130),
        "r_hand": (158, 158),
    }


def shoulder_press(phase: float) -> dict:
    press = dip(phase)
    return {
        "head": (120, 68),
        "neck": (120, 98),
        "hip": (120, 132),
        "l_knee": (110, 172),
        "l_foot": (106, GROUND),
        "r_knee": (130, 172),
        "r_foot": (134, GROUND),
        "l_elbow": (98 - 14 * press, 116 - 34 * press),
        "l_hand": (104 - 20 * press, 96 - 62 * press),
        "r_elbow": (142 + 14 * press, 116 - 34 * press),
        "r_hand": (136 + 20 * press, 96 - 62 * press),
    }


def stretch(phase: float) -> dict:
    bend = sway(phase)
    return {
        "head": (120 + 16 * bend, 68),
        "neck": (120 + 6 * bend, 96),
        "hip": (120, 130),
        "l_knee": (110, 172),
        "l_foot": (106, GROUND),
        "r_knee": (130, 172),
        "r_foot": (134, GROUND),
        "l_elbow": (120 + 12 * bend, 74),
        "l_hand": (120 + 30 * bend, 44),
        "r_elbow": (108, 116),
        "r_hand": (100, 146),
    }


POSES = {
    "SQUAT": squat,
    "LUNGE": lunge,
    "GLUTE_BRIDGE": glute_bridge,
    "CALF_RAISE": calf_raise,
    "PUSH_UP": push_up,
    "DUMBBELL_ROW": dumbbell_row,
    "SHOULDER_PRESS": shoulder_press,
    "BICEP_CURL": bicep_curl,
    "PLANK": plank,
    "SIDE_PLANK": side_plank,
    "DEAD_BUG": dead_bug,
    "CRUNCH": crunch,
    "BURPEE": burpee,
    "MOUNTAIN_CLIMBER": mountain_climber,
    "JUMPING_JACK": jumping_jack,
    "STRETCH": stretch,
}


def draw_frame(pose: dict) -> Image.Image:
    scale = SUPERSAMPLE
    canvas = Image.new("RGBA", (SIZE * scale, SIZE * scale), (0, 0, 0, 0))
    draw = ImageDraw.Draw(canvas)

    def point(name: str) -> tuple:
        x, y = pose[name]
        return (x * scale, y * scale)

    draw.line(
        [(30 * scale, GROUND * scale), (210 * scale, GROUND * scale)],
        fill=FLOOR,
        width=3 * scale,
    )
    for start, end in BONES:
        if start in pose and end in pose:
            draw.line([point(start), point(end)], fill=INK, width=WIDTH * scale)
    head = point("head")
    radius = HEAD_RADIUS * scale
    draw.ellipse(
        [head[0] - radius, head[1] - radius, head[0] + radius, head[1] + radius],
        outline=INK,
        width=WIDTH * scale,
    )
    return canvas.resize((SIZE, SIZE), Image.LANCZOS)


def palette_frame(frame: Image.Image) -> Image.Image:
    """Index 0 is transparent, index 1 the ink: a GIF has no alpha channel."""
    indexed = Image.new("P", (SIZE, SIZE), 0)
    palette = [0, 0, 0, INK[0], INK[1], INK[2]] + [0, 0, 0] * 254
    indexed.putpalette(palette)
    mask = frame.getchannel("A").point(lambda value: 255 if value > 96 else 0)
    indexed.paste(1, mask=mask)
    return indexed


def programme_exercises() -> list:
    source = PROGRAMME.read_text()
    block = re.search(r"enum class ProgrammeExercise\(.*?\) \{(.*?)\n\}", source, re.S)
    if not block:
        sys.exit("could not find the ProgrammeExercise enum in WorkoutProgramme.kt")
    return re.findall(r"^    ([A-Z][A-Z_]*)\(\s*ProgrammeFocus", block.group(1), re.M)


def main() -> None:
    exercises = programme_exercises()
    if not exercises:
        sys.exit("the ProgrammeExercise enum looks empty")

    missing = [name for name in exercises if name not in POSES]
    extra = [name for name in POSES if name not in exercises]
    if missing or extra:
        sys.exit(f"clips and enum disagree: missing={missing} extra={extra}")

    OUTPUT.mkdir(parents=True, exist_ok=True)
    contact = Image.new("RGBA", (SIZE * 4, SIZE * ((len(exercises) + 3) // 4)), (255, 255, 255, 255))

    for index, name in enumerate(exercises):
        frames = [palette_frame(draw_frame(POSES[name](step / FRAMES))) for step in range(FRAMES)]
        path = OUTPUT / f"exercise_{name.lower()}.gif"
        frames[0].save(
            path,
            save_all=True,
            append_images=frames[1:],
            duration=FRAME_MS,
            loop=0,
            transparency=0,
            disposal=2,
            optimize=True,
        )
        written = Image.open(path)
        if getattr(written, "n_frames", 1) != FRAMES:
            sys.exit(
                f"{path.name} came out with {written.n_frames} frames instead of {FRAMES}: "
                "two frames were identical, so give the pose a little drift"
            )
        contact.paste(draw_frame(POSES[name](0.5)), ((index % 4) * SIZE, (index // 4) * SIZE))

    contact.convert("RGB").save(PREVIEW)
    total = sum((OUTPUT / f"exercise_{name.lower()}.gif").stat().st_size for name in exercises)
    print(f"{len(exercises)} clips, {total / 1024:.0f} KiB, preview at {PREVIEW}")


if __name__ == "__main__":
    main()
