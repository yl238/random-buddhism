package org.randomdhamma.data

enum class ContentType { SUTTA, TALK }

enum class PickSource { DAILY, MANUAL }

/** A resolved teaching ready to open: a sutta page or a talk MP3. */
data class Pick(
    val type: ContentType,
    val url: String,
    val title: String,
)

/** A morning talk parsed from an archive page. */
data class Talk(
    val year: Int,
    val mp3Url: String,
    val title: String,
)
