package com.example.model

enum class AudioQuality(
    val id: String,
    val displayName: String,
    val shortName: String,
    val bitrateKbps: Int,
    val badge: String,
    val description: String,
    val fileSuffix: String
) {
    ULTRA_HIGH(
        id = "ultra_high",
        displayName = "Ultra High Quality",
        shortName = "320k Ultra HQ",
        bitrateKbps = 320,
        badge = "320 kbps • Ultra HQ",
        description = "Lossless-grade 320 kbps AAC/M4A master studio audio",
        fileSuffix = "_320.mp4"
    ),
    HIGH(
        id = "high",
        displayName = "High Quality",
        shortName = "160k HQ",
        bitrateKbps = 160,
        badge = "160 kbps • HQ",
        description = "Crystal clear 160 kbps AAC audio stream",
        fileSuffix = "_160.mp4"
    ),
    STANDARD(
        id = "standard",
        displayName = "Standard Quality",
        shortName = "96k Normal",
        bitrateKbps = 96,
        badge = "96 kbps • Standard",
        description = "Balanced 96 kbps audio for smooth streaming",
        fileSuffix = "_96.mp4"
    ),
    DATA_SAVER(
        id = "data_saver",
        displayName = "Data Saver",
        shortName = "48k Saver",
        bitrateKbps = 48,
        badge = "48 kbps • Saver",
        description = "Optimized 48 kbps stream for saving mobile cellular data",
        fileSuffix = "_48.mp4"
    );

    companion object {
        val DEFAULT = ULTRA_HIGH

        fun fromId(id: String?): AudioQuality =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT

        fun fromBitrate(bitrate: Int?): AudioQuality {
            if (bitrate == null) return ULTRA_HIGH
            return when {
                bitrate >= 256 -> ULTRA_HIGH
                bitrate >= 140 -> HIGH
                bitrate >= 80 -> STANDARD
                else -> DATA_SAVER
            }
        }
    }
}
