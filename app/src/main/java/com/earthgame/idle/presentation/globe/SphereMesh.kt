package com.earthgame.idle.presentation.globe

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * A UV sphere, built once and reused by every shell the globe draws.
 *
 * Interleaved as `position(3), normal(3), uv(2)` so one vertex buffer feeds
 * all three attributes with a single bind. The sphere is a unit sphere: the
 * atmosphere and cloud shells are the same mesh drawn at a larger scale rather
 * than separate geometry, which is the cheapest way to get three shells out of
 * one upload.
 *
 * Latitude runs from the north pole down, and `v` runs with it, so `v = 0` is
 * the top row of an equirectangular texture — the orientation
 * [EarthSurface.createEarthBitmap] writes.
 */
class SphereMesh(segments: Int, rings: Int) {

    val vertexBuffer: FloatBuffer
    val indexBuffer: ShortBuffer
    val indexCount: Int

    init {
        val vertices = ArrayList<Float>((segments + 1) * (rings + 1) * FLOATS_PER_VERTEX)
        for (ring in 0..rings) {
            val v = ring.toFloat() / rings
            val theta = v * Math.PI          // 0 at the north pole
            val sinTheta = sin(theta).toFloat()
            val cosTheta = cos(theta).toFloat()
            for (segment in 0..segments) {
                val u = segment.toFloat() / segments
                // The half-turn offset puts longitude 0 at the centre of the
                // texture, matching how the land field is laid out.
                val phi = (u * 2.0 * Math.PI) - Math.PI
                val sinPhi = sin(phi).toFloat()
                val cosPhi = cos(phi).toFloat()

                val x = sinTheta * sinPhi
                val y = cosTheta
                val z = sinTheta * cosPhi

                vertices.add(x); vertices.add(y); vertices.add(z)
                vertices.add(x); vertices.add(y); vertices.add(z)
                vertices.add(u); vertices.add(v)
            }
        }

        val indices = ArrayList<Short>(segments * rings * 6)
        val stride = segments + 1
        for (ring in 0 until rings) {
            for (segment in 0 until segments) {
                val a = (ring * stride + segment).toShort()
                val b = (ring * stride + segment + 1).toShort()
                val c = ((ring + 1) * stride + segment).toShort()
                val d = ((ring + 1) * stride + segment + 1).toShort()
                indices.add(a); indices.add(c); indices.add(b)
                indices.add(b); indices.add(c); indices.add(d)
            }
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                for (value in vertices) put(value)
                position(0)
            }

        indexBuffer = ByteBuffer
            .allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                for (value in indices) put(value)
                position(0)
            }

        indexCount = indices.size
    }

    companion object {
        const val FLOATS_PER_VERTEX = 8
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
        const val NORMAL_OFFSET_BYTES = 3 * 4
        const val UV_OFFSET_BYTES = 6 * 4
    }
}

/**
 * Converts a latitude/longitude in degrees to a point on the unit sphere, in
 * the same frame [SphereMesh] is built in.
 *
 * The one place the mapping is written down. Storm markers, event markers and
 * wind particles all go through it, so a storm the simulation says is at 14°N
 * 62°W is drawn exactly where the texture puts 14°N 62°W.
 */
fun latLonToUnitSphere(latitudeDeg: Float, longitudeDeg: Float, out: FloatArray, offset: Int = 0) {
    val latitude = Math.toRadians(latitudeDeg.toDouble())
    val longitude = Math.toRadians(longitudeDeg.toDouble())
    val cosLatitude = cos(latitude)
    out[offset] = (cosLatitude * sin(longitude)).toFloat()
    out[offset + 1] = sin(latitude).toFloat()
    out[offset + 2] = (cosLatitude * cos(longitude)).toFloat()
}
