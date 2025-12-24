data class OAuthResult(
    val accessToken: String?,
    val state: String?,
    val raw: String
)

object OAuthRedirectParser {

    fun parse(rawUrl: String): OAuthResult {
        // rawUrl can be:
        // senzo://animeapp#access_token=...&token_type=Bearer&expires_in=...&state=...
        // or sometimes: senzo://animeapp?access_token=...&state=...
        val uri = kotlin.runCatching { android.net.Uri.parse(rawUrl) }.getOrNull()

        // 1) Try fragment: after '#'
        val fragment = uri?.fragment.orEmpty()
        val tokenFromFragment = getFromParams(fragment, "access_token")
        val stateFromFragment = getFromParams(fragment, "state")

        // 2) Try normal query: after '?'
        val tokenFromQuery = uri?.getQueryParameter("access_token")
        val stateFromQuery = uri?.getQueryParameter("state")

        // 3) Fallback regex on full string
        val tokenFromRegex = Regex("""access_token=([^&]+)""")
            .find(rawUrl)?.groupValues?.getOrNull(1)

        val stateFromRegex = Regex("""state=([^&]+)""")
            .find(rawUrl)?.groupValues?.getOrNull(1)

        val token = tokenFromFragment ?: tokenFromQuery ?: tokenFromRegex
        val state = stateFromFragment ?: stateFromQuery ?: stateFromRegex

        return OAuthResult(
            accessToken = token,
            state = state,
            raw = rawUrl
        )
    }

    private fun getFromParams(paramString: String, key: String): String? {
        if (paramString.isBlank()) return null
        // paramString looks like: access_token=XXX&token_type=Bearer&state=YYY
        return paramString.split("&")
            .mapNotNull {
                val idx = it.indexOf("=")
                if (idx <= 0) null else it.substring(0, idx) to it.substring(idx + 1)
            }
            .firstOrNull { it.first == key }
            ?.second
    }
}
