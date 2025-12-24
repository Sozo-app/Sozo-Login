package com.azamovme.sozotvlogin.ui.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.azamovme.sozotvlogin.R
import com.azamovme.sozotvlogin.data.pref.TokenStore
import com.azamovme.sozotvlogin.databinding.SplashScreenBinding
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class SplashScreen : Fragment() {

    private var _binding: SplashScreenBinding? = null
    private val binding get() = _binding!!

    private val tokenStore: TokenStore by inject()
    private val scope = MainScope()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = SplashScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scope.launch {
            delay(1000)
            val token = tokenStore.readToken()
            if (!token.isNullOrBlank()) {
                findNavController().navigate(
                    R.id.action_splashScreen_to_profileFragment, null,
                    NavOptions.Builder().setPopUpTo(R.id.splashScreen, true).build()
                )
            } else {
                findNavController().navigate(
                    R.id.action_splashScreen_to_loginFragment, null,
                    NavOptions.Builder().setPopUpTo(R.id.splashScreen, true).build()
                )
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
