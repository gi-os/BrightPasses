package com.gios.lightpass.nav

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * The venue, handed to whatever knows how to walk you there.
 *
 * A ticket knows the one thing this app cannot do anything with: where the place is. The
 * venue sits on the stub as text and every phone here has BrightWay on it, so this is one
 * tap and no typing — which is the whole feature, because "Barclays Center" retyped into
 * a search box at the top of a subway staircase is exactly when nobody wants to type.
 *
 * Two intents, in order:
 *
 *  - `brightway://go?q=<venue>` — BrightWay's own, addressed to it by name so a browser
 *    cannot answer for it. Needs the `<queries>` entry in the manifest to be visible at
 *    all on API 30 and up.
 *  - `geo:0,0?q=<venue>` — the standard shape, for a phone without BrightWay. `0,0` is
 *    the convention for "no coordinates, here are words instead".
 *
 * Words, not coordinates, and BrightWay lands on its search results rather than starting
 * a route: a parsed venue name is a guess, and a guess should be confirmed by the person
 * holding the phone before anything starts giving them directions.
 */
object Directions {

    private const val BRIGHTWAY = "com.gios.brightway"

    /** Is there anything on this phone to hand a place to? */
    fun available(context: Context): Boolean =
        context.packageManager.resolveActivity(standard("test"), 0) != null

    /** True if the handoff was accepted by something. */
    fun open(context: Context, venue: String?): Boolean {
        val q = venue?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val brightway = Intent(Intent.ACTION_VIEW, Uri.parse("brightway://go?q=${Uri.encode(q)}"))
            .setPackage(BRIGHTWAY)
        for (intent in listOf(brightway, standard(q))) {
            val ok = runCatching {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            if (ok) return true
        }
        return false
    }

    private fun standard(q: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(q)}"))
}
