package dev.tidytv.core.ops

/**
 * A picture of what the TV is showing right now (`screencap -p`, which every Android TV with ADB has), for people making
 * custom icons or reporting a problem. Protected video (Netflix and the like) shows black, which is the TV's own rule.
 */
object TvScreenshot {
    const val PATH = "/data/local/tmp/tidytv-shot.png"
    const val TAKE = "screencap -p $PATH && echo ok"
    const val CLEAN = "rm -f $PATH"
    private val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)

    /** The PNG bytes, or null when the TV didn't make one. The picture is deleted from the TV either way. */
    suspend fun take(tv: TvShell): ByteArray? {
        val made = Parse.lines(tv.run(TAKE)).contains("ok")
        val bytes = if (made) tv.pull(PATH) else null
        tv.run(CLEAN)
        return bytes?.takeIf { it.size > 64 && it.copyOf(4).contentEquals(PNG) }
    }
}
