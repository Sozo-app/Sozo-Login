package com.azamovme.sozotvlogin.data.repository

import com.apollographql.apollo3.ApolloClient
import com.azamovme.sozotvlogin.GetViewerQuery
import com.azamovme.sozotvlogin.data.pref.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class AuthRepository(
    private val apollo: ApolloClient,
    private val tokenStore: TokenStore
) {
    private val clientId = "14066"
    private val redirectUri = "senzo://animeapp"
    private val authBaseUrl = "https://anilist.co/api/v2/oauth/authorize"

    fun buildLoginUrl(): String {
        val url =
            "$authBaseUrl?client_id=$clientId&response_type=token"
        return url
    }

    suspend fun handleRedirect(url: String, expectedState: String?): String? {
        val parsed = OAuthRedirectParser.parse(url)
        val token = parsed.accessToken ?: return "Access token not found"
        if (!expectedState.isNullOrBlank() && parsed.state != expectedState) return "State mismatch"
        tokenStore.saveToken(token)
        return null
    }

    data class ViewerUi(val id: String, val name: String, val avatarUrl: String?)

    suspend fun getViewer(): ViewerUi = withContext(Dispatchers.IO) {
        val res = apollo.query(GetViewerQuery()).execute()
        val v = res.data?.Viewer
        ViewerUi(
            id = v?.id?.toString().orEmpty(),
            name = v?.name.orEmpty(),
            avatarUrl = v?.avatar?.large
        )
    }
}
