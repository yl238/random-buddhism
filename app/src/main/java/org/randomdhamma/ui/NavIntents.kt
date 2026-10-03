package org.randomdhamma.ui

import android.content.Context
import android.content.Intent
import org.randomdhamma.data.ContentType
import org.randomdhamma.data.Pick

/** Intents that open MainActivity on a specific screen (from notifications and the media session). */
object NavIntents {
    const val EXTRA_TARGET = "target"
    const val EXTRA_URL = "url"
    const val EXTRA_TITLE = "title"

    const val TARGET_SUTTA = "sutta"
    const val TARGET_TALK = "talk"
    const val TARGET_PLAYER = "player"
    const val TARGET_RESOLVE = "resolve"

    private fun base(context: Context, target: String) =
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TARGET, target)

    fun forPick(context: Context, pick: Pick): Intent =
        base(context, if (pick.type == ContentType.SUTTA) TARGET_SUTTA else TARGET_TALK)
            .putExtra(EXTRA_URL, pick.url)
            .putExtra(EXTRA_TITLE, pick.title)

    fun openPlayer(context: Context): Intent = base(context, TARGET_PLAYER)

    fun resolve(context: Context): Intent = base(context, TARGET_RESOLVE)
}
