package org.randomdhamma.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player

/** Replaces whatever is playing with the talk at [url] and starts it at [speed]. */
fun Player.playTalk(url: String, title: String?, speed: Float) {
    val item = MediaItem.Builder()
        .setMediaId(url)
        .setUri(url)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title ?: "Morning talk")
                .setArtist("Ṭhānissaro Bhikkhu")
                .setAlbumTitle("Short morning talks")
                .build()
        )
        .build()
    setMediaItem(item)
    setPlaybackSpeed(speed)
    prepare()
    play()
}
