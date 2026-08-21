package si.merhar.roamer

/**
 * Retention logic for the on-screen rewrite log.
 *
 * Kept separate from [PreferencesRepository] so the trimming rules are pure and testable
 * on the JVM, with no DataStore or Android context involved.
 */
object RewriteLog {

    /** Number of entries retained before the oldest are dropped. */
    const val MAX_ENTRIES = 20

    /**
     * Returns [existing] with [entry] prepended, keeping at most [maxEntries] lines.
     *
     * Newest first, because the UI shows the top of the log. Blank lines are dropped so a
     * stored value that starts or ends with a newline does not consume retention slots.
     *
     * @param existing Current log contents, newline-separated
     * @param entry New entry to prepend; ignored when blank
     * @param maxEntries Maximum lines to retain
     */
    fun prepend(existing: String, entry: String, maxEntries: Int = MAX_ENTRIES): String {
        val lines = existing.lines().filter { it.isNotBlank() }
        if (entry.isBlank()) return lines.take(maxEntries).joinToString("\n")
        return (listOf(entry) + lines).take(maxEntries).joinToString("\n")
    }
}
