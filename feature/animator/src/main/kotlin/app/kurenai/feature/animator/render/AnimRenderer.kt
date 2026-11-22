package app.kurenai.feature.animator.render

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.GLSurfaceView
import app.kurenai.core.model.MotionIntensity
import app.kurenai.feature.animator.model.AnimationPlan
import app.kurenai.feature.animator.model.CameraShot
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Real-time 2D mesh-deformation renderer. It samples the page texture through a
 * GPU-displaced grid, crops the current panel from the director plan, and adds
 * breathing/head-sway deformation plus a light vignette. 60 fps target.
 */
class AnimRenderer(
    private val bitmap: Bitmap?,
    initialPlan: AnimationPlan?,
) : GLSurfaceView.Renderer {

    private var program = 0
    private var texture = 0
    private var aPos = 0
    private var aUv = 0
    private var uTime = 0
    private var uRect = 0
    private var uIntensity = 0
    private var plan: AnimationPlan? = initialPlan
    private var startedAt = System.nanoTime()
    @Volatile var playing: Boolean = true
    @Volatile var playheadMs: Long = 0L
    private val segments = 24

    fun updatePlan(plan: AnimationPlan?) { this.plan = plan }
    fun reset() { startedAt = System.nanoTime(); playheadMs = 0L }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUv = GLES20.glGetAttribLocation(program, "aUv")
        uTime = GLES20.glGetUniformLocation(program, "uTime")
        uRect = GLES20.glGetUniformLocation(program, "uRect")
        uIntensity = GLES20.glGetUniformLocation(program, "uIntensity")
        texture = loadTexture(bitmap ?: createFallback())
        GLES20.glClearColor(0.07f, 0.045f, 0.05f, 1f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (playing) {
            playheadMs = (System.nanoTime() - startedAt) / 1_000_000L
        }
        val plan = plan ?: return
        val shot = shotAt(plan, playheadMs) ?: return
        val progress = if (shot.durationMs == 0L) 0f else ((playheadMs - shot.startMs).toFloat() / shot.durationMs).coerceIn(0f, 1f)
        val l = lerp(shot.fromLeft, shot.toLeft, progress)
        val t = lerp(shot.fromTop, shot.toTop, progress)
        val r = lerp(shot.fromRight, shot.toRight, progress)
        val b = lerp(shot.fromBottom, shot.toBottom, progress)

        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform1f(uTime, (System.nanoTime() - startedAt) / 1_000_000_000f)
        GLES20.glUniform4f(uRect, l, t, r, b)
        GLES20.glUniform1f(
            uIntensity,
            when (plan.intensity) {
                MotionIntensity.STILL -> 0f
                MotionIntensity.SUBTLE -> 0.25f
                MotionIntensity.CINEMATIC -> 0.55f
                MotionIntensity.FULL -> 1f
            },
        )
        drawMesh(progress, plan)
    }

    private fun drawMesh(progress: Float, plan: AnimationPlan) {
        val count = (segments + 1) * (segments + 1)
        val vertices = FloatArray(count * 3)
        val uvs = FloatArray(count * 2)
        var vi = 0
        var ui = 0
        for (y in 0..segments) {
            for (x in 0..segments) {
                val fx = x.toFloat() / segments
                val fy = y.toFloat() / segments
                vertices[vi++] = fx * 2f - 1f
                vertices[vi++] = 1f - fy * 2f
                vertices[vi++] = 0f
                uvs[ui++] = fx
                uvs[ui++] = fy
            }
        }
        val indices = ShortArray(segments * segments * 6)
        var ii = 0
        for (y in 0 until segments) {
            for (x in 0 until segments) {
                val topLeft = (y * (segments + 1) + x).toShort()
                val topRight = (topLeft + 1).toShort()
                val bottomLeft = ((y + 1) * (segments + 1) + x).toShort()
                val bottomRight = (bottomLeft + 1).toShort()
                indices[ii++] = topLeft
                indices[ii++] = bottomLeft
                indices[ii++] = topRight
                indices[ii++] = topRight
                indices[ii++] = bottomLeft
                indices[ii++] = bottomRight
            }
        }
        val vBuf = vertices.toBuffer()
        val uBuf = uvs.toBuffer()
        val iBuf = ByteBuffer.allocateDirect(indices.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(indices).also { it.position(0) }
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, vBuf)
        GLES20.glEnableVertexAttribArray(aUv)
        GLES20.glVertexAttribPointer(aUv, 2, GLES20.GL_FLOAT, false, 0, uBuf)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indices.size, GLES20.GL_UNSIGNED_SHORT, iBuf)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aUv)
    }

    private fun shotAt(plan: AnimationPlan, timeMs: Long): CameraShot? =
        plan.shots.lastOrNull { timeMs >= it.startMs } ?: plan.shots.firstOrNull()

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun loadTexture(bitmap: Bitmap): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return ids[0]
    }

    private fun createFallback(): Bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
        eraseColor(android.graphics.Color.rgb(42, 28, 31))
        val canvas = android.graphics.Canvas(this)
        val paint = android.graphics.Paint().apply { color = android.graphics.Color.rgb(226, 61, 79); isAntiAlias = true }
        canvas.drawCircle(32f, 36f, 16f, paint)
        paint.color = android.graphics.Color.rgb(255, 244, 237)
        canvas.drawRect(16f, 58f, 48f, 88f, paint)
    }

    private fun buildProgram(vertex: String, fragment: String): Int {
        val v = compile(GLES20.GL_VERTEX_SHADER, vertex)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fragment)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        return p
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun FloatArray.toBuffer(): FloatBuffer =
        ByteBuffer.allocateDirect(size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(this).also { it.position(0) }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPos;
            attribute vec2 aUv;
            uniform float uTime;
            uniform vec4 uRect;
            uniform float uIntensity;
            varying vec2 vUv;
            varying float vLuma;
            void main() {
                float weight = 1.0 - aPos.y * aPos.y;
                vec3 p = aPos.xyz;
                p.x += sin(uTime * 1.1 + aPos.y *
2.2) * 0.006 * uIntensity * weight;
                p.y += sin(uTime * 1.7) * 0.0035 * uIntensity;
                vUv = mix(uRect.xy, uRect.zw, aUv);
                vLuma = 0.5 + 0.5 * aPos.y;
                gl_Position = vec4(p, 1.0);
            }
        """
        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vUv;
            varying float vLuma;
            uniform sampler2D uTexture;
            void main() {
                vec4 color = texture2D(uTexture, vUv);
                float vignette = smoothstep(0.85, 0.2, distance(vUv, vec2(0.5)));
                color.rgb *= mix(0.72, 1.04, vignette * vLuma);
                gl_FragColor = color;
            }
        """
    }
}

class AnimatedGLSurfaceView(context: Context, renderer: AnimRenderer) : GLSurfaceView(context) {
    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }
}
