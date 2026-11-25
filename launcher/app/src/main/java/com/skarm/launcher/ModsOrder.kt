package com.skarm.launcher

/**
 * The order mods are applied in, worked out automatically; there is no manual order.
 *
 * A later mod wins when two change the same file. The rule is SKradle's "optimal" order
 * (`core::mods::list`) and SKapsule-iOS's `SKModOrder`: the mods that change the most files go
 * first, equal ones by name, so a small, targeted mod comes later and beats a broad one. It is a
 * total order, so it agrees for every pair of mods and does not depend on the order the
 * filesystem lists them in (which `File.listFiles` leaves undefined).
 */
object ModsOrder {
    /**
     * How many files a mod writes, from the entry names of one archive: not a directory, not
     * `mod.json` / `mod.png` (at any depth, as extraction skips them), not a protected game file
     * ([protectedFiles], lowercase, compared case-insensitively) and not a path that climbs out of
     * the extraction root. Exactly the entries that `ModsApplier.extractZipToDirectory` writes.
     */
    fun filesWritten(entryNames: Sequence<String>, protectedFiles: Set<String>): Int =
        entryNames.count { name ->
            !name.endsWith("/") &&
                !isMetadata(name) &&
                name.lowercase() !in protectedFiles &&
                staysInside(name)
        }

    /**
     * [mods] in apply order: most files first, then by name ignoring case, then by exact name.
     * [filesChanged] is read once per mod.
     */
    fun <T> optimal(mods: List<T>, name: (T) -> String, filesChanged: (T) -> Int): List<T> =
        mods.map { Triple(it, name(it), filesChanged(it)) }
            .sortedWith(
                compareByDescending<Triple<T, String, Int>> { it.third }
                    .thenBy { it.second.lowercase() }
                    .thenBy { it.second },
            )
            .map { it.first }

    private fun isMetadata(name: String): Boolean {
        val base = name.substringAfterLast('/')
        return base == "mod.json" || base == "mod.png"
    }

    /** False for an empty or absolute name, or one whose `..` components leave the root. */
    private fun staysInside(name: String): Boolean {
        if (name.isEmpty() || name.startsWith("/") || name.startsWith("\\") || '\u0000' in name) return false
        var depth = 0
        for (part in name.split('/')) {
            when (part) {
                "", "." -> Unit
                ".." -> if (--depth < 0) return false
                else -> depth++
            }
        }
        return true
    }
}
