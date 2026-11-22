package app.kurenai.feature.animator.model

import androidx.compose.runtime.Immutable
import app.kurenai.core.model.MotionIntensity
import app.kurenai.core.model.TtsProviderKind

@Immutable
data class PanelRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val order: Int = 0,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val aspect: Float get() = if (height == 0f) 0f else width / height
}

@Immutable
data class Bubble(
    val panelIndex: Int,
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val speakerId: String? = null,
    val isNarration: Boolean = false,
    val isSfx: Boolean = false,
)

@Immutable
data class CastMember(
    val id: String,
    val name: String,
    val gender: String = "unknown",
    val age: String = "unknown",
    val personality: String = "calm",
    val voiceName: String? = null,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val emotion: String = "neutral",
)

@Immutable
data class CharacterRig(
    val id: String,
    val panelIndex: Int,
    val head: RigPart,
    val torso: RigPart,
    val leftArm: RigPart,
    val rightArm: RigPart,
    val hair: RigPart,
    val expression: String = "neutral",
    val parameters: RigParameters = RigParameters(),
)

@Immutable
data class RigPart(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val rotation: Float = 0f,
)

@Immutable
data class RigParameters(
    val headTurn: Float = 0f,
    val bodySway: Float = 0f,
    val breathing: Float = 0.35f,
    val blink: Float = 0f,
    val eyeGazeX: Float = 0f,
    val eyeGazeY: Float = 0f,
    val brow: Float = 0f,
    val mouthOpen: Float = 0f,
    val hairMotion: Float = 0.25f,
)

@Immutable
data class CameraShot(
    val panelIndex: Int,
    val startMs: Long,
    val durationMs: Long,
    val fromLeft: Float,
    val fromTop: Float,
    val fromRight: Float,
    val fromBottom: Float,
    val toLeft: Float,
    val toTop: Float,
    val toRight: Float,
    val toBottom: Float,
    val pushIn: Float = 0f,
    val parallax: Float = 0.12f,
    val shake: Float = 0f,
    val transition: TransitionKind = TransitionKind.CUT,
)

enum class TransitionKind { CUT, DISSOLVE, SLIDE, PARALLAX }

@Immutable
data class AnimationPlan(
    val panels: List<PanelRect>,
    val bubbles: List<Bubble>,
    val rigs: List<CharacterRig>,
    val shots: List<CameraShot>,
    val rightToLeft: Boolean,
    val durationMs: Long,
    val intensity: MotionIntensity,
    val warnings: List<String> = emptyList(),
) {
    val panelCount: Int get() = panels.size
}

@Immutable
data class VoiceCasting(
    val narrator: CastMember = CastMember("narrator", "Narrator", "unknown", "adult", "warm"),
    val characters: List<CastMember> = emptyList(),
    val sfxVoice: CastMember? = null,
    val provider: TtsProviderKind = TtsProviderKind.SYSTEM,
    val autoCast: Boolean = true,
)

@Immutable
data class PlaybackState(
    val playing: Boolean = false,
    val prepared: Boolean = false,
    val progress: Float = 0f,
    val currentPanel: Int = 0,
    val subtitle: String = "",
    val speaker: String? = null,
    val error: String? = null,
    val processing: Boolean = false,
    val estimatedSeconds: Int = 0,
)
