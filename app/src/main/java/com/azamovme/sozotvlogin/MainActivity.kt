package com.azamovme.sozotvlogin

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.azamovme.sozotvlogin.data.repository.AuthRepository
import com.azamovme.sozotvlogin.databinding.ActivityMainBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val authRepo: AuthRepository by inject()
    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
    fun startLogin() {
        val url = authRepo.buildLoginUrl()
        CustomTabsIntent.Builder().build().launchUrl(this, url.toUri())
    }

    private fun handleIntent(intent: Intent?) {
        val url = intent?.dataString ?: intent?.data?.toString()
        if (url.isNullOrBlank()) return

        scope.launch {
            val err = authRepo.handleRedirect(url, expectedState = null)
            val nav = findNavController(R.id.navHost)

            if (err == null) {
                val options = NavOptions.Builder()
                    .setPopUpTo(R.id.splashScreen, true)
                    .build()
                nav.navigate(R.id.profileFragment, null, options)
            } else {
                nav.currentBackStackEntry?.savedStateHandle?.set("login_error", err)
            }
        }
    }
}
