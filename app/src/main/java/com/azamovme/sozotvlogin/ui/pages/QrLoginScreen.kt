package com.azamovme.sozotvlogin.ui.pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.azamovme.sozotvlogin.databinding.QrLoginScreenBinding
import com.azamovme.sozotvlogin.data.pref.TokenStore
import com.google.firebase.Firebase
import com.google.firebase.database.FirebaseDatabase
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors

class QrLoginScreen : Fragment() {

    private var _binding: QrLoginScreenBinding? = null
    private val binding get() = _binding!!

    private val tokenStore: TokenStore by inject()
    private val firebaseDatabase: FirebaseDatabase by inject()
    private val scope = MainScope()

    private var camera: Camera? = null
    private val executor = Executors.newSingleThreadExecutor()

    private var isHandled = false

    private val scanner by lazy {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    private sealed class UiState {
        data object Scanning : UiState()
        data object Sending : UiState()
        data class Error(val msg: String) : UiState()
        data object Success : UiState()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = QrLoginScreenBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setState(UiState.Scanning)

        binding.btnClose.setOnClickListener {
            findNavController().popBackStack()
        }

        binding.btnFlash.setOnClickListener {
            val c = camera ?: return@setOnClickListener
            val enabled = c.cameraInfo.torchState.value == androidx.camera.core.TorchState.ON
            c.cameraControl.enableTorch(!enabled)
        }

        if (hasCameraPermission()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }

    private fun setState(state: UiState) {
        when (state) {
            UiState.Scanning -> {
                binding.progress.visibility = View.GONE
                binding.tvStatus.text = "Ready to scan TV QR..."
                isHandled = false
            }

            UiState.Sending -> {
                binding.progress.visibility = View.VISIBLE
                binding.tvStatus.text = "Connecting..."
            }

            is UiState.Error -> {
                binding.progress.visibility = View.GONE
                binding.tvStatus.text = state.msg
                isHandled = false
            }

            UiState.Success -> {
                binding.progress.visibility = View.GONE
                binding.tvStatus.text = "Connected."
                findNavController().popBackStack()
                Toast.makeText(requireActivity(), "Tv connected", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            findNavController().popBackStack()
        }
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()

            val preview = Preview.Builder()
                .build()
                .also { it.surfaceProvider = binding.previewView.surfaceProvider }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy ->
                processFrame(proxy)
            }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis
                )
            } catch (e: Exception) {
                setState(UiState.Error("Camera error: ${e.message ?: "unknown"}"))
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processFrame(imageProxy: ImageProxy) {
        if (isHandled) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (isHandled) return@addOnSuccessListener

                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (!raw.isNullOrBlank()) {
                    isHandled = true
                    handleTvPairQr(raw)
                }
            }
            .addOnFailureListener {
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * TV QR expected payload:
     * {"t":"tv_pair","sid":"SESSION_ID","v":1}
     * Fallback: raw string = sid
     */
    private fun handleTvPairQr(raw: String) {
        val sid = extractSid(raw)
        if (sid.isNullOrBlank()) {
            setState(UiState.Error("Invalid TV QR code"))
            return
        }

        setState(UiState.Sending)

        scope.launch {
            val token = tokenStore.readToken()
            if (token.isNullOrBlank()) {
                setState(UiState.Error("Login token not found. Please login again."))
                return@launch
            }

            val ref = firebaseDatabase
                .getReference("tv_pair_sessions")
                .child(sid)

            ref.get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        setState(UiState.Error("Session not found or expired."))
                        return@addOnSuccessListener
                    }

                    val exp = snap.child("expiresAt").getValue(Long::class.java) ?: Long.MAX_VALUE
                    if (System.currentTimeMillis() > exp) {
                        setState(UiState.Error("QR expired. Please refresh on TV."))
                        return@addOnSuccessListener
                    }

                    // write token for TV
                    ref.child("token").setValue(token)
                        .addOnSuccessListener {
                            ref.child("status").setValue("paired")

                            setState(UiState.Success)

                            findNavController().previousBackStackEntry
                                ?.savedStateHandle
                                ?.set("tv_pair_result", "TV connected ✅")

                            findNavController().popBackStack()
                        }
                        .addOnFailureListener {
                            setState(UiState.Error("Failed to connect. Try again."))
                        }
                }
                .addOnFailureListener {
                    setState(UiState.Error("Network error. Try again."))
                }
        }
    }

    private fun extractSid(raw: String): String? {
        return try {
            val obj = JSONObject(raw)
            if (obj.optString("t") == "tv_pair") obj.optString("sid") else null
        } catch (_: Exception) {
            raw.trim().takeIf { it.length in 4..64 }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        executor.shutdown()
        scanner.close()
    }

    companion object {
        private const val REQ_CAMERA = 2001
    }
}
