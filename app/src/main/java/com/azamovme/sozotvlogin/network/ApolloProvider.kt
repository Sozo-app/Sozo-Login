package com.azamovme.sozotvlogin.network

import com.apollographql.apollo3.ApolloClient
import com.apollographql.apollo3.network.okHttpClient
import com.azamovme.sozotvlogin.data.pref.TokenStore
import okhttp3.Interceptor
import okhttp3.OkHttpClient

class ApolloProvider(
    private val tokenStore: TokenStore
) {
    private val baseUrl = "https://graphql.anilist.co"

    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore.readTokenBlocking()
        val req = chain.request().newBuilder().apply {
            if (!token.isNullOrBlank()) header("Authorization", "Bearer $token")
        }.build()
        chain.proceed(req)
    }

    val apollo: ApolloClient by lazy {
        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .build()

        ApolloClient.Builder()
            .serverUrl(baseUrl)
            .okHttpClient(okHttp)
            .build()
    }
}
