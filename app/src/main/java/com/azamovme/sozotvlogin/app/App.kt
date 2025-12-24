package com.azamovme.sozotvlogin.app


import android.app.Application
import com.azamovme.sozotvlogin.di.appModule
import com.azamovme.sozotvlogin.di.firebaseModule
import com.google.firebase.FirebaseApp
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val app = FirebaseApp.initializeApp(this)
        if (app == null) {
            throw IllegalStateException("FirebaseApp.initializeApp returned null. Check google-services.json and plugin.")
        }
        startKoin {
            androidContext(this@App)
            modules(appModule, firebaseModule)
        }
    }
}
