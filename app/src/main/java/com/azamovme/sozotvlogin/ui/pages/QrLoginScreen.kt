package com.azamovme.sozotvlogin.ui.pages

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.azamovme.sozotvlogin.R
import com.azamovme.sozotvlogin.data.pref.TokenStore
import com.azamovme.sozotvlogin.databinding.QrLoginScreenBinding
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.koin.android.ext.android.inject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class QrLoginScreen : Fragment() {

    private var _binding: QrLoginScreenBinding? = null
    private val binding get() = _binding!!

    private val tokenStore: TokenStore by inject()
    private val firebaseDatabase: FirebaseDatabase by inject()

    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val handled = AtomicBoolean(false)

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

        binding.btnClose.setOnClickListener { findNavController().popBackStack() }

        binding.btnFlash.setOnClickListener {
            val c = camera ?: return@setOnClickListener
            val enabled = c.cameraInfo.torchState.value == androidx.camera.core.TorchState.ON
            c.cameraControl.enableTorch(!enabled)
        }

        if (hasCameraPermission()) startCamera()
        else requestPermissions(arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
    }

    private fun withBinding(block: (QrLoginScreenBinding) -> Unit) {
        val b = _binding ?: return
        if (!isAdded) return
        block(b)
    }

    private fun setState(state: UiState) {
        withBinding { b ->
            when (state) {
                UiState.Scanning -> {
                    b.progress.visibility = View.GONE
                    b.tvStatus.text = getString(R.string.ready_to_scan_tv_qr)
                    handled.set(false)
                }
                UiState.Sending -> {
                    b.progress.visibility = View.VISIBLE
                    b.tvStatus.text = getString(R.string.connecting)
                }
                is UiState.Error -> {
                    b.progress.visibility = View.GONE
                    b.tvStatus.text = state.msg
                    handled.set(false)
                }
                UiState.Success -> {
                    b.progress.visibility = View.GONE
                    b.tvStatus.text = getString(R.string.connected)
                }
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
        if (requestCode == REQ_CAMERA &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) startCamera()
        else findNavController().popBackStack()
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(requireContext())
        future.addListener({
            val provider = future.get()
            cameraProvider = provider

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(executor) { proxy -> processFrame(proxy) }

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
        if (_binding == null) {
            imageProxy.close()
            return
        }

        if (handled.get()) {
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
                if (_binding == null) return@addOnSuccessListener
                if (handled.get()) return@addOnSuccessListener

                val raw = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                if (!raw.isNullOrBlank()) {
                    if (handled.compareAndSet(false, true)) {
                        view?.post { handleTvPairQr(raw) }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("PAIR", "MLKit scan error", e)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleTvPairQr(raw: String) {
        if (_binding == null) {
            handled.set(false)
            return
        }

        val sid = extractSid(raw)
        if (sid.isNullOrBlank()) {
            setState(UiState.Error("Invalid TV QR code"))
            handled.set(false)
            return
        }

        setState(UiState.Sending)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val token = tokenStore.readToken()
                if (token.isNullOrBlank()) {
                    setState(UiState.Error("Login token not found. Please login again."))
                    handled.set(false)
                    return@launch
                }

                val ref = firebaseDatabase.getReference("tv_pair_sessions").child(sid)

                val snap = withTimeout(10_000L) { ref.get().await() }
                Log.d("PAIR", "sid=$sid exists=${snap.exists()} path=$ref")

                if (!snap.exists()) {
                    setState(UiState.Error("Session not found. Refresh QR on TV."))
                    handled.set(false)
                    return@launch
                }

                val update = hashMapOf<String, Any>(
                    "token" to token,
                    "status" to "paired",
                    "pairedAt" to ServerValue.TIMESTAMP
                )

                withTimeout(10_000L) { ref.updateChildren(update).await() }

                setState(UiState.Success)

                findNavController().previousBackStackEntry
                    ?.savedStateHandle
                    ?.set("tv_pair_result", "TV connected ✅")

                Toast.makeText(requireContext(), "TV connected ✅", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()

            } catch (e: Exception) {
                val msg = when (e) {
                    is TimeoutCancellationException -> "Timeout. Check internet / Firebase rules."
                    else -> (e.message ?: e.javaClass.simpleName)
                }
                Log.e("PAIR", "pair failed", e)
                setState(UiState.Error("Connect failed: $msg"))
                handled.set(false)
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
        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
        cameraProvider = null
        camera = null

        executor.shutdownNow()

        try { scanner.close() } catch (_: Exception) {}

        _binding = null
        super.onDestroyView()
    }

    companion object {
        private const val REQ_CAMERA = 2001
    }
}
