package com.taphunter.phone.den

import android.graphics.Color
import android.opengl.GLES20.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A tiny solid-geometry kit for the phone den: everything is triangles with
 * per-vertex color, lit by one warm lamp plus a rim glow, fog toward the
 * habitat's horizon. Color alpha is repurposed as EMISSIVE amount, which is
 * what makes lanterns, gems, and creature accents bloom against the dusk.
 */
class MeshBuilder {
    private val v = ArrayList<Float>(4096)

    fun tri(
        ax: Float, ay: Float, az: Float,
        bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float,
        color: Int, emissive: Float
    ) {
        // Face normal.
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val wx = cx - ax; val wy = cy - ay; val wz = cz - az
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        nx /= len; ny /= len; nz /= len
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        for (p in arrayOf(
            floatArrayOf(ax, ay, az), floatArrayOf(bx, by, bz), floatArrayOf(cx, cy, cz)
        )) {
            v.add(p[0]); v.add(p[1]); v.add(p[2])
            v.add(nx); v.add(ny); v.add(nz)
            v.add(r); v.add(g); v.add(b); v.add(emissive)
        }
    }

    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        color: Int, emissive: Float = 0f, doubleSided: Boolean = false
    ) {
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, color, emissive)
        tri(ax, ay, az, cx, cy, cz, dx, dy, dz, color, emissive)
        if (doubleSided) {
            tri(cx, cy, cz, bx, by, bz, ax, ay, az, color, emissive)
            tri(dx, dy, dz, cx, cy, cz, ax, ay, az, color, emissive)
        }
    }

    /** Ellipsoid; the workhorse of every round creature body. */
    fun ellipsoid(
        cx: Float, cy: Float, cz: Float, rx: Float, ry: Float, rz: Float,
        color: Int, emissive: Float = 0f, lats: Int = 8, lons: Int = 12
    ) {
        for (i in 0 until lats) {
            val t0 = Math.PI * i / lats; val t1 = Math.PI * (i + 1) / lats
            for (j in 0 until lons) {
                val p0 = 2 * Math.PI * j / lons; val p1 = 2 * Math.PI * (j + 1) / lons
                fun pt(t: Double, p: Double) = floatArrayOf(
                    cx + (sin(t) * cos(p)).toFloat() * rx,
                    cy + cos(t).toFloat() * ry,
                    cz + (sin(t) * sin(p)).toFloat() * rz
                )
                val a = pt(t0, p0); val b = pt(t0, p1); val c = pt(t1, p1); val d = pt(t1, p0)
                tri(a[0], a[1], a[2], b[0], b[1], b[2], c[0], c[1], c[2], color, emissive)
                tri(a[0], a[1], a[2], c[0], c[1], c[2], d[0], d[1], d[2], color, emissive)
            }
        }
    }

    /** Cone from a base disc center along an axis (unit-ish) by height. */
    fun cone(
        cx: Float, cy: Float, cz: Float, radius: Float,
        axX: Float, axY: Float, axZ: Float, height: Float,
        color: Int, emissive: Float = 0f, sides: Int = 8
    ) {
        // Build an orthonormal-ish frame around the axis.
        val al = kotlin.math.sqrt(axX * axX + axY * axY + axZ * axZ).coerceAtLeast(1e-6f)
        val ax = axX / al; val ay = axY / al; val az = axZ / al
        val refX = if (kotlin.math.abs(ay) < 0.9f) 0f else 1f
        val refY = if (kotlin.math.abs(ay) < 0.9f) 1f else 0f
        var ux = refY * az - 0f * ay; var uy = 0f * ax - refX * az; var uz = refX * ay - refY * ax
        val ul = kotlin.math.sqrt(ux * ux + uy * uy + uz * uz).coerceAtLeast(1e-6f)
        ux /= ul; uy /= ul; uz /= ul
        val wx = ay * uz - az * uy; val wy = az * ux - ax * uz; val wz = ax * uy - ay * ux
        val tipX = cx + ax * height; val tipY = cy + ay * height; val tipZ = cz + az * height
        for (j in 0 until sides) {
            val p0 = 2 * Math.PI * j / sides; val p1 = 2 * Math.PI * (j + 1) / sides
            fun rim(p: Double) = floatArrayOf(
                cx + (cos(p).toFloat() * ux + sin(p).toFloat() * wx) * radius,
                cy + (cos(p).toFloat() * uy + sin(p).toFloat() * wy) * radius,
                cz + (cos(p).toFloat() * uz + sin(p).toFloat() * wz) * radius
            )
            val a = rim(p0); val b = rim(p1)
            tri(a[0], a[1], a[2], b[0], b[1], b[2], tipX, tipY, tipZ, color, emissive)
            tri(b[0], b[1], b[2], a[0], a[1], a[2], cx, cy, cz, color, emissive)
        }
    }

    fun box(
        cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float,
        color: Int, emissive: Float = 0f
    ) {
        val x0 = cx - hx; val x1 = cx + hx
        val y0 = cy - hy; val y1 = cy + hy
        val z0 = cz - hz; val z1 = cz + hz
        quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, color, emissive)
        quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, color, emissive)
        quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, color, emissive)
        quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, color, emissive)
        quad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, color, emissive)
        quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, color, emissive)
    }

    fun bake(): Mesh {
        val fb = ByteBuffer.allocateDirect(v.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(v.toFloatArray()).position(0)
        return Mesh(fb, v.size / 10)
    }
}

class Mesh(private val buf: FloatBuffer, private val count: Int) {
    fun draw(aPos: Int, aNrm: Int, aCol: Int) {
        if (count == 0) return
        buf.position(0); glVertexAttribPointer(aPos, 3, GL_FLOAT, false, 40, buf)
        buf.position(3); glVertexAttribPointer(aNrm, 3, GL_FLOAT, false, 40, buf)
        buf.position(6); glVertexAttribPointer(aCol, 4, GL_FLOAT, false, 40, buf)
        glDrawArrays(GL_TRIANGLES, 0, count)
    }
}

object DenGL {
    const val MAIN_VS = """
        uniform mat4 uVP; uniform mat4 uM;
        attribute vec3 aPos; attribute vec3 aNrm; attribute vec4 aCol;
        varying vec3 vW; varying vec3 vN; varying vec4 vC;
        void main() {
            vec4 w = uM * vec4(aPos, 1.0);
            vW = w.xyz;
            vN = normalize((uM * vec4(aNrm, 0.0)).xyz);
            vC = aCol;
            gl_Position = uVP * w;
        }"""

    const val MAIN_FS = """
        precision mediump float;
        uniform vec3 uCam; uniform vec3 uLight; uniform vec3 uFog; uniform vec3 uRim;
        varying vec3 vW; varying vec3 vN; varying vec4 vC;
        void main() {
            vec3 n = normalize(vN);
            float lit = max(dot(n, uLight), 0.0) * 0.6 + 0.4;
            vec3 view = normalize(uCam - vW);
            float rim = pow(1.0 - max(dot(n, view), 0.0), 2.6);
            vec3 base = vC.rgb * lit + uRim * rim * 0.32 + vC.rgb * vC.a * 1.1;
            float fog = clamp((length(uCam - vW) - 9.0) / 16.0, 0.0, 0.85);
            gl_FragColor = vec4(mix(base, uFog, fog), 1.0);
        }"""

    const val SKY_VS = """
        attribute vec2 aPos; varying float vY;
        void main() { vY = aPos.y * 0.5 + 0.5; gl_Position = vec4(aPos, 0.999, 1.0); }"""

    const val SKY_FS = """
        precision mediump float;
        uniform vec3 uTop; uniform vec3 uBot; varying float vY;
        void main() { gl_FragColor = vec4(mix(uBot, uTop, pow(vY, 0.8)), 1.0); }"""

    const val PT_VS = """
        uniform mat4 uVP;
        attribute vec3 aPos; attribute vec4 aCol; attribute float aSize;
        varying vec4 vC;
        void main() {
            vC = aCol;
            gl_Position = uVP * vec4(aPos, 1.0);
            gl_PointSize = aSize / max(gl_Position.w, 0.4);
        }"""

    const val PT_FS = """
        precision mediump float;
        varying vec4 vC;
        void main() {
            float d = length(gl_PointCoord - vec2(0.5));
            float a = smoothstep(0.5, 0.12, d) * vC.a;
            gl_FragColor = vec4(vC.rgb, a);
        }"""

    fun program(vs: String, fs: String): Int {
        fun sh(type: Int, src: String): Int {
            val s = glCreateShader(type)
            glShaderSource(s, src); glCompileShader(s)
            val ok = IntArray(1); glGetShaderiv(s, GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException(glGetShaderInfoLog(s))
            return s
        }
        val p = glCreateProgram()
        glAttachShader(p, sh(GL_VERTEX_SHADER, vs))
        glAttachShader(p, sh(GL_FRAGMENT_SHADER, fs))
        glLinkProgram(p)
        return p
    }
}
