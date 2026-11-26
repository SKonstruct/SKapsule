package com.skarm.launcher

/**
 * The GLFW key behind a typed character, on a US keyboard. A desktop keyboard sends key press, the character, then
 * key release, and the game's text fields only act on the typed text once the matching key release arrives: with the
 * character alone it shows up but a live search or filter does not update until Enter or Backspace (real key
 * transitions) is used. The same rules and cases as the iOS app (SKGlfwKeys.h).
 */
internal object GlfwKeys {
    const val UNKNOWN = -1
    const val MOD_SHIFT = 1

    private const val SHIFTED_DIGITS = ")!@#$%^&*("

    /** The key that types [cp] (with Shift where it needs it), or [UNKNOWN] for text that has no key. */
    fun keyFor(cp: Int): Int = when {
        cp in 'a'.code..'z'.code -> 'A'.code + (cp - 'a'.code)
        cp in 'A'.code..'Z'.code -> cp
        cp in '0'.code..'9'.code -> cp
        cp > 0 && cp < 128 && SHIFTED_DIGITS.indexOf(cp.toChar()) >= 0 -> '0'.code + SHIFTED_DIGITS.indexOf(cp.toChar())
        cp !in 32..126 -> UNKNOWN // toChar() would cut a wider code point down to an ASCII one
        else -> when (cp.toChar()) {
            ' ' -> 32
            '\'', '"' -> 39
            ',', '<' -> 44
            '-', '_' -> 45
            '.', '>' -> 46
            '/', '?' -> 47
            ';', ':' -> 59
            '=', '+' -> 61
            '[', '{' -> 91
            '\\', '|' -> 92
            ']', '}' -> 93
            '`', '~' -> 96
            else -> UNKNOWN
        }
    }

    /** Whether typing [cp] holds Shift on a US keyboard. */
    fun shifted(cp: Int): Boolean =
        cp in 'A'.code..'Z'.code || (cp > 0 && cp < 128 && cp.toChar() in "!@#$%^&*()\"<_>?:+{|}~")
}
