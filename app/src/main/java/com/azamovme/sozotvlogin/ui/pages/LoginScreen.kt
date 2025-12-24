package com.azamovme.sozotvlogin.ui.pages

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import com.azamovme.sozotvlogin.MainActivity
import com.azamovme.sozotvlogin.R
import com.azamovme.sozotvlogin.databinding.LoginScreenBinding


class LoginScreen : Fragment(R.layout.login_screen) {

    private var _binding: LoginScreenBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = LoginScreenBinding.bind(view)

        binding.btnWebsite.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://sozo.azamov.me".toUri())
            startActivity(intent)
        }
        binding.btnTelegram.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, "https://t.me/sozoapp".toUri())
            startActivity(intent)
        }
        binding.login.setOnClickListener {
            (requireActivity() as MainActivity).startLogin()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
