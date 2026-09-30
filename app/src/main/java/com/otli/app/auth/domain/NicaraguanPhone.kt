package com.otli.app.auth.domain

/**
 * Nicaraguan phone numbers: 8 digits with an optional `+505` / `505` prefix. Spaces and dashes are
 * tolerated anywhere; anything else is rejected. Valid input normalizes to `+505XXXXXXXX`.
 */
object NicaraguanPhone {
    private const val COUNTRY_CODE = "505"
    private const val LOCAL_DIGITS = 8
    private val separators = Regex("""[\s-]""")

    /** Returns the normalized number, or null when [raw] is not a valid Nicaraguan number. */
    fun normalize(raw: String): String? {
        val compact = raw.replace(separators, "")
        val digits = when {
            compact.startsWith("+$COUNTRY_CODE") -> compact.removePrefix("+$COUNTRY_CODE")
            compact.length == COUNTRY_CODE.length + LOCAL_DIGITS && compact.startsWith(COUNTRY_CODE) ->
                compact.removePrefix(COUNTRY_CODE)
            else -> compact
        }
        val valid = digits.length == LOCAL_DIGITS && digits.all { it in '0'..'9' }
        return if (valid) "+$COUNTRY_CODE$digits" else null
    }

    fun isValid(raw: String): Boolean = normalize(raw) != null
}
