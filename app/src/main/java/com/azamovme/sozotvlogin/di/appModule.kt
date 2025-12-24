package com.azamovme.sozotvlogin.di

import com.azamovme.sozotvlogin.data.pref.TokenStore
import com.azamovme.sozotvlogin.data.repository.AuthRepository
import com.azamovme.sozotvlogin.network.ApolloProvider
import com.google.firebase.database.FirebaseDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val appModule = module {
    single { TokenStore(androidContext()) }
    single { ApolloProvider(tokenStore = get()).apollo }
    single { AuthRepository(apollo = get(), tokenStore = get()) }


}
val firebaseModule = module {
    single {
        FirebaseDatabase.getInstance(
            "https://sozo-app-a36e6-default-rtdb.asia-southeast1.firebasedatabase.app/"
        )
    }
}
