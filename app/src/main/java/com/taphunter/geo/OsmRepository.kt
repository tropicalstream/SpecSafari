package com.taphunter.geo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

data class OsmRoad(val id: Long, val kind: String, val name: String?, val pts: List<GeoPoint>) {
    val isPath = kind in PATH_KINDS
    val isMajor = kind in MAJOR_KINDS
    companion object {
        val PATH_KINDS = setOf("footway", "path", "steps", "track", "cycleway", "bridleway", "pedestrian")
        val MAJOR_KINDS = setOf("motorway", "trunk", "primary", "secondary")
    }
}

data class OsmPoi(val id: Long, val name: String?, val category: String, val p: GeoPoint)

/**
 * OpenStreetMap around the hunter, via Overpass, with a tile disk cache so a
 * walk that was fetched once keeps working with no connectivity. The world
 * downloads in ~600 m tiles as the wearer moves; a spawn beyond the data edge
 * triggers a prefetch around the quarry itself.
 */
class OsmRepository(
    context: Context,
    private val onData: (Collection<OsmRoad>, Collection<OsmPoi>) -> Unit
) {
    private val cacheDir = File(context.cacheDir, "osm").apply { mkdirs() }
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val busy = AtomicBoolean()

    private val roads = LinkedHashMap<Long, OsmRoad>()
    private val pois = LinkedHashMap<Long, OsmPoi>()
    private val fetchedCenters = mutableListOf<GeoPoint>()
    var lastError: String? = null; private set
    var tilesLoaded = 0; private set

    fun snapshotRoads(): List<OsmRoad> = synchronized(this) { roads.values.toList() }
    fun snapshotPois(): List<OsmPoi> = synchronized(this) { pois.values.toList() }

    /** Make sure data exists around a point; cheap to call every few seconds. */
    fun ensureAround(p: GeoPoint) {
        val near = synchronized(this) { fetchedCenters.any { GeoMath.distanceM(it, p) < 380f } }
        if (near) return
        if (!busy.compareAndSet(false, true)) return
        executor.execute {
            try {
                val tile = tileCenter(p)
                val json = loadTile(tile)
                if (json != null) {
                    val (r, q) = parse(json)
                    synchronized(this) {
                        fetchedCenters += tile
                        for (road in r) roads[road.id] = road
                        for (poi in q) pois[poi.id] = poi
                        prune(p)
                        tilesLoaded++
                    }
                    lastError = null
                    val rs = snapshotRoads(); val qs = snapshotPois()
                    main.post { onData(rs, qs) }
                }
            } finally {
                busy.set(false)
            }
        }
    }

    /** Round to a ~600 m grid so cache files are reused between sessions. */
    private fun tileCenter(p: GeoPoint): GeoPoint {
        val step = 0.005
        return GeoPoint(
            (p.lat / step).roundToInt() * step,
            (p.lon / step).roundToInt() * step
        )
    }

    private fun cacheFile(tile: GeoPoint) =
        File(cacheDir, "t_%.4f_%.4f.json".format(tile.lat, tile.lon))

    private fun loadTile(tile: GeoPoint): String? {
        val f = cacheFile(tile)
        val fresh = f.isFile && System.currentTimeMillis() - f.lastModified() < 7L * 24 * 3600 * 1000
        if (fresh) return runCatching { f.readText() }.getOrNull()
        val net = runCatching { fetch(tile) }.onFailure {
            Log.w(TAG, "overpass", it)
            lastError = it.message ?: "network"
        }.getOrNull()
        if (net != null) {
            runCatching { f.writeText(net) }
            return net
        }
        // Offline: a stale tile beats an empty map.
        return if (f.isFile) runCatching { f.readText() }.getOrNull() else null
    }

    private fun fetch(c: GeoPoint): String {
        // Roads out to 700 m per tile (tiles are ~600 m apart so coverage overlaps);
        // POIs — the RPG places where creatures and treasure appear — out to 1100 m.
        val q = """
            [out:json][timeout:15];
            (
              way(around:700,${c.lat},${c.lon})["highway"]["highway"!~"construction|proposed|elevator|corridor"];
            );
            out geom;
            (
              nw(around:1100,${c.lat},${c.lon})["leisure"~"^(park|garden|nature_reserve|playground|dog_park|pitch|golf_course)${'$'}"];
              nw(around:1100,${c.lat},${c.lon})["highway"="trailhead"];
              nw(around:1100,${c.lat},${c.lon})["tourism"~"^(museum|attraction|viewpoint|artwork|picnic_site)${'$'}"];
              nw(around:1100,${c.lat},${c.lon})["amenity"~"^(cafe|restaurant|fast_food|bar|pub|library|school|college|university|place_of_worship|fountain|marketplace|cinema|theatre|fuel|pharmacy|bank|post_office|townhall)${'$'}"];
              nw(around:1100,${c.lat},${c.lon})["shop"];
              nw(around:1100,${c.lat},${c.lon})["historic"];
              nw(around:1100,${c.lat},${c.lon})["railway"="station"];
              nw(around:1100,${c.lat},${c.lon})["natural"~"^(peak|spring|beach)${'$'}"];
            );
            out center;
        """.trimIndent()
        var failure: Throwable? = null
        for (endpoint in listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )) {
            try { return request(endpoint, q) } catch (t: Throwable) { failure = t }
        }
        throw failure ?: IllegalStateException("no overpass endpoint")
    }

    private fun request(endpoint: String, query: String): String {
        val con = URL(endpoint).openConnection() as HttpURLConnection
        con.requestMethod = "POST"
        con.connectTimeout = 10_000; con.readTimeout = 20_000; con.doOutput = true
        con.setRequestProperty("User-Agent", "TapHunter-X3/1.0")
        con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
        con.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
        if (con.responseCode !in 200..299) throw IllegalStateException("overpass HTTP ${con.responseCode}")
        return con.inputStream.bufferedReader().readText()
    }

    private fun parse(json: String): Pair<List<OsmRoad>, List<OsmPoi>> {
        val rs = mutableListOf<OsmRoad>()
        val qs = mutableListOf<OsmPoi>()
        val elements = JSONObject(json).optJSONArray("elements") ?: return rs to qs
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i) ?: continue
            val tags = e.optJSONObject("tags") ?: continue
            val geometry = e.optJSONArray("geometry")
            if (geometry != null && tags.has("highway") && tags.optString("highway") != "trailhead") {
                val pts = (0 until geometry.length())
                    .mapNotNull { geometry.optJSONObject(it) }
                    .map { GeoPoint(it.optDouble("lat"), it.optDouble("lon")) }
                if (pts.size > 1) rs += OsmRoad(
                    e.optLong("id"), tags.optString("highway"),
                    tags.optString("name").ifBlank { null }, pts
                )
            } else {
                val pos = e.optJSONObject("center") ?: e
                val lat = pos.optDouble("lat"); val lon = pos.optDouble("lon")
                if (lat == 0.0 && lon == 0.0) continue
                val category = when {
                    tags.optString("railway") == "station" -> "station"
                    tags.optString("highway") == "trailhead" -> "trailhead"
                    tags.has("leisure") -> "leisure=${tags.optString("leisure")}"
                    tags.has("historic") -> "historic"
                    tags.has("tourism") -> "tourism=${tags.optString("tourism")}"
                    tags.has("amenity") -> "amenity=${tags.optString("amenity")}"
                    tags.has("shop") -> "shop=${tags.optString("shop")}"
                    tags.has("natural") -> "natural=${tags.optString("natural")}"
                    else -> null
                } ?: continue
                qs += OsmPoi(e.optLong("id"), tags.optString("name").ifBlank { null }, category,
                    GeoPoint(lat, lon))
            }
        }
        return rs to qs
    }

    /** Bound memory: drop geometry far behind the wearer. */
    private fun prune(around: GeoPoint) {
        if (roads.size > 900) {
            val far = roads.values.filter { r ->
                r.pts.none { GeoMath.distanceM(it, around) < 4500f }
            }.map { it.id }
            for (id in far) roads.remove(id)
        }
        if (pois.size > 1200) {
            val far = pois.values.filter { GeoMath.distanceM(it.p, around) > 5500f }.map { it.id }
            for (id in far) pois.remove(id)
        }
        if (fetchedCenters.size > 60) {
            fetchedCenters.removeAll { GeoMath.distanceM(it, around) > 5000f }
        }
    }

    companion object { private const val TAG = "OsmRepository" }
}
