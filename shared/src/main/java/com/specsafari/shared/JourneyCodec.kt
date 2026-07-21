package com.specsafari.shared

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32

/** Privacy-safe daily field record. It deliberately carries only a broad
 * postal/administrative region—never coordinates, addresses, or streets. */
data class JourneyRecord(
    val day: String,
    val distanceM: Int,
    val treasures: Int,
    val essence: Int,
    val berries: Int,
    val foundSpecies: List<Int>,
    val releasedSpecies: List<Int>,
    val region: String,
) {
    fun summary(): String {
        fun names(ids: List<Int>): String = ids
            .groupingBy { it }.eachCount().entries.joinToString(", ") { (id, count) ->
                val name = Species.ALL.getOrNull(id)?.name ?: "UNKNOWN CREATURE"
                if (count > 1) "$name ×$count" else name
            }
        val distance = if (distanceM < 1000) "$distanceM m"
            else String.format("%.2f km", distanceM / 1000f)
        val items = buildList {
            if (treasures > 0) add("$treasures treasure${if (treasures == 1) "" else "s"}")
            if (essence > 0) add("$essence essence")
            if (berries > 0) add("$berries ${if (berries == 1) "berry" else "berries"}")
        }.ifEmpty { listOf("no trail items") }.joinToString(", ")
        val found = if (foundSpecies.isEmpty()) "no creatures" else names(foundSpecies)
        val released = if (releasedSpecies.isEmpty()) "none released" else "released ${names(releasedSpecies)}"
        val habitats = (foundSpecies + releasedSpecies).distinct().mapNotNull {
            Species.ALL.getOrNull(it)?.habitat?.lowercase()
        }.distinct().ifEmpty { listOf("the local streets") }.joinToString(", ")
        val where = region.ifBlank { "the local region" }
        return "On $day I walked $distance in $where, found $items and $found, $released, " +
            "and explored habitats including $habitats."
    }
}

/** A self-contained, offline-shareable journey code with an integrity hash.
 * The checksum detects mistyped or truncated codes; no server is required. */
object JourneyCodec {
    private const val PREFIX = "SSJ1"

    private fun safe(s: String) = s.replace('|', '/').replace(',', '/').trim()

    fun encode(j: JourneyRecord): String {
        val raw = listOf(
            safe(j.day),
            j.distanceM.coerceAtLeast(0).toString(),
            j.treasures.coerceAtLeast(0).toString(),
            j.essence.coerceAtLeast(0).toString(),
            j.berries.coerceAtLeast(0).toString(),
            j.foundSpecies.joinToString(","),
            j.releasedSpecies.joinToString(","),
            safe(j.region),
        ).joinToString("|")
        val bytes = raw.toByteArray(StandardCharsets.UTF_8)
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val crc = CRC32().apply { update(bytes) }.value.toString(16).padStart(8, '0')
        return "$PREFIX.$payload.$crc"
    }

    fun decode(code: String): JourneyRecord? = runCatching {
        val parts = code.trim().split('.')
        if (parts.size != 3 || parts[0] != PREFIX) return null
        val bytes = Base64.getUrlDecoder().decode(parts[1])
        val expected = CRC32().apply { update(bytes) }.value.toString(16).padStart(8, '0')
        if (!expected.equals(parts[2], ignoreCase = true)) return null
        val p = String(bytes, StandardCharsets.UTF_8).split('|')
        if (p.size != 8) return null
        fun ids(s: String) = s.split(',').mapNotNull { it.toIntOrNull() }
            .filter { it in Species.ALL.indices }.take(64)
        JourneyRecord(
            day = p[0].take(16),
            distanceM = p[1].toInt().coerceIn(0, 200_000),
            treasures = p[2].toInt().coerceIn(0, 999),
            essence = p[3].toInt().coerceIn(0, 999_999),
            berries = p[4].toInt().coerceIn(0, 9999),
            foundSpecies = ids(p[5]),
            releasedSpecies = ids(p[6]),
            region = p[7].take(80),
        )
    }.getOrNull()
}
