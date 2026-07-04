package org.tasks.location

/**
 * A parsed `@gomu-geo v1` marker (the gomu Cowork to Tasks.org geofence bridge).
 *
 * Contract (one line, on its own line, anywhere in the task notes):
 *
 *     @gomu-geo v1 lat=<dec> lng=<dec> [r=<int_m>] [arrive=on|off] [depart=on|off] [place="<name>"]
 *
 * lat and lng are required decimal degrees. arrive and depart are null when absent
 * from the marker; when either is present the missing one is treated as off by the
 * caller. Keep this in sync with the contract documented in the gomu wiki and bump
 * the version when the grammar changes.
 */
data class GeoMarker(
    val lat: Double,
    val lng: Double,
    val radius: Int? = null,
    val arrive: Boolean? = null,
    val depart: Boolean? = null,
    val place: String? = null,
) {
    val hasFlags: Boolean
        get() = arrive != null || depart != null
}

private const val MARKER_TAG = "@gomu-geo"
private const val MARKER_VERSION = "v1"

/**
 * Parse the first `@gomu-geo v1` line from [notes]. Returns null when there is no
 * valid marker: missing notes, no marker line, a non-v1 version, or a missing or
 * out-of-range lat/lng. Key order is free, extra whitespace is tolerated, unknown
 * keys are ignored, and the first marker line wins. Pure: no Android dependencies.
 */
fun parseGeoMarker(notes: String?): GeoMarker? {
    val line = notes?.lineSequence()?.firstOrNull { isGeoMarkerLine(it) } ?: return null
    val tokens = tokenize(line.trim())
    if (tokens.size < 2 || tokens[0] != MARKER_TAG || tokens[1] != MARKER_VERSION) {
        return null
    }
    val values = HashMap<String, String>()
    for (i in 2 until tokens.size) {
        val eq = tokens[i].indexOf('=')
        if (eq <= 0) continue
        val key = tokens[i].substring(0, eq)
        if (key !in values) {
            values[key] = unquote(tokens[i].substring(eq + 1))
        }
    }
    val lat = values["lat"]?.toDoubleOrNull() ?: return null
    val lng = values["lng"]?.toDoubleOrNull() ?: return null
    // NaN/Infinity slip past a bare range check (every comparison with NaN is
    // false), so reject non-finite coords explicitly before the bounds test.
    if (!lat.isFinite() || !lng.isFinite() ||
        lat < -90.0 || lat > 90.0 || lng < -180.0 || lng > 180.0) {
        return null
    }
    return GeoMarker(
        lat = lat,
        lng = lng,
        // A non-positive radius is rejected by the geofence registrar; drop it so
        // the marker falls back to the app default rather than failing to arm.
        radius = values["r"]?.toIntOrNull()?.takeIf { it > 0 },
        arrive = parseOnOff(values["arrive"]),
        depart = parseOnOff(values["depart"]),
        place = values["place"]?.takeIf { it.isNotBlank() },
    )
}

/**
 * Remove the first `@gomu-geo` line from [notes] so a promoted marker can never
 * double-fire and the cleaned note re-syncs up. Returns the remaining notes, or
 * null when nothing meaningful remains. Pure.
 */
fun stripGeoMarker(notes: String?): String? {
    if (notes == null) {
        return null
    }
    val lines = notes.split("\n")
    var removed = false
    val kept = ArrayList<String>(lines.size)
    for (line in lines) {
        if (!removed && isGeoMarkerLine(line)) {
            removed = true
        } else {
            kept.add(line)
        }
    }
    if (!removed) {
        return notes
    }
    // No global trim: the user's own leading/trailing whitespace is their content —
    // only the marker line (and its separator) goes away.
    return kept.joinToString("\n").ifBlank { null }
}

private fun isGeoMarkerLine(line: String): Boolean {
    val trimmed = line.trim()
    return trimmed == MARKER_TAG || trimmed.startsWith("$MARKER_TAG ")
}

private fun parseOnOff(value: String?): Boolean? = when (value?.lowercase()) {
    "on" -> true
    "off" -> false
    else -> null
}

private fun unquote(value: String): String =
    if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
        value.substring(1, value.length - 1)
    } else {
        value
    }

/** Split on whitespace, keeping double-quoted spans (and their `key=` prefix) intact. */
private fun tokenize(line: String): List<String> {
    val tokens = ArrayList<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (c in line) {
        when {
            c == '"' -> {
                inQuotes = !inQuotes
                current.append(c)
            }
            c.isWhitespace() && !inQuotes -> {
                if (current.isNotEmpty()) {
                    tokens.add(current.toString())
                    current.setLength(0)
                }
            }
            else -> current.append(c)
        }
    }
    if (current.isNotEmpty()) {
        tokens.add(current.toString())
    }
    return tokens
}
