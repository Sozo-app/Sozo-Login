package com.azamovme.sozotvlogin.ui.pages

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.azamovme.sozotvlogin.R
import com.azamovme.sozotvlogin.data.pref.TokenStore
import com.azamovme.sozotvlogin.data.repository.AuthRepository
import com.azamovme.sozotvlogin.databinding.ProfileScreenBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class ProfileScreen : Fragment(R.layout.profile_screen) {

    private var _binding: ProfileScreenBinding? = null
    private val binding get() = _binding!!

    private val repo: AuthRepository by inject()
    private val tokenStore: TokenStore by inject()
    private val scope = MainScope()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = ProfileScreenBinding.bind(view)
        findNavController().currentBackStackEntry
            ?.savedStateHandle
            ?.getLiveData<String>("tv_pair_result")
            ?.observe(viewLifecycleOwner) { msg ->
                if (!msg.isNullOrBlank()) {
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                    findNavController().currentBackStackEntry?.savedStateHandle?.remove<String>("tv_pair_result")
                }
            }

        binding.btnConnectTv.setOnClickListener {
            findNavController().navigate(R.id.action_profile_to_qr)
        }

        binding.btnLogout.setOnClickListener {
            scope.launch {
                tokenStore.clear()
                findNavController().navigate(R.id.loginFragment)
            }
        }

        loadViewer()
    }

    @SuppressLint("SetTextI18n")
    private fun loadViewer() {
        binding.progress.visibility = View.VISIBLE
        binding.tvError.visibility = View.GONE

        scope.launch {
            try {
                val v = repo.getViewer()
                binding.progress.visibility = View.GONE

                binding.tvName.text = v.name
                binding.tvId.text = "User ID: ${v.id}"

                if (!v.avatarUrl.isNullOrBlank()) {
                    binding.imgAvatar.visibility = View.VISIBLE
                    binding.imgAvatar.load(v.avatarUrl)
                } else {
                    binding.imgAvatar.visibility = View.GONE
                }
            } catch (t: Throwable) {
                binding.progress.visibility = View.GONE
                binding.tvError.visibility = View.VISIBLE
                binding.tvError.text = t.message ?: "Failed to load profile"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
