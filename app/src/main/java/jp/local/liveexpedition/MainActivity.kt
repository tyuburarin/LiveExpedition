package jp.local.liveexpedition

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked by mutableStateOf(false)
    private var error by mutableStateOf("")
    private var ready by mutableStateOf(false)
    private var prompting = false
    private lateinit var model: AppModel
    private lateinit var prompt: BiometricPrompt

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setRecentsScreenshotEnabled(false)
        model = androidx.lifecycle.ViewModelProvider(this)[AppModel::class.java]
        prompt =
            BiometricPrompt(
                this,
                ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        lifecycleScope.launch {
                            if (model.open()) {
                                unlocked =
                                    lifecycle.currentState.isAtLeast(
                                        androidx.lifecycle.Lifecycle.State.RESUMED
                                    )
                                ready = true
                                error = ""
                            } else {
                                error = "暗号化データを開けません。データは削除していません。"
                            }
                            prompting = false
                        }
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        prompting = false
                        unlocked = false
                        error = "認証できませんでした。再試行してください。"
                    }

                    override fun onAuthenticationFailed() {
                        unlocked = false
                        error = "認証できませんでした。"
                    }
                },
            )
        setContent {
            val mode by model.theme.collectAsState(initial = "charcoal")
            val accent by model.accent.collectAsState(initial = "blue")
            val dark =
                when (mode) {
                    "system" -> androidx.compose.foundation.isSystemInDarkTheme()
                    "light" -> false
                    else -> true
                }
            val screenState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            ExpeditionTheme(dark, mode, accent) {
                ExternalHost(model) {
                    Surface(Modifier.fillMaxSize()) {
                        if (unlocked && ready)
                            screenState.SaveableStateProvider("authorized") {
                                AppScreen(model) { protected ->
                                    if (protected)
                                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                                }
                            }
                        else
                            Column(
                                Modifier.fillMaxSize().padding(32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("ライブ・遠征管理", style = MaterialTheme.typography.headlineMedium)
                                Spacer(Modifier.height(16.dp))
                                Text("端末の認証でデータを開きます")
                                Text(
                                    "検証版です。実際の機密情報は登録しないでください。",
                                    Modifier.padding(vertical = 16.dp),
                                )
                                if (error.isNotBlank()) Text(error)
                                Button(onClick = { authenticate() }) { Text("ロック解除") }
                            }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (!model.lockEnabled.first()) {
                if (
                    model.open() &&
                        lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
                ) {
                    ready = true
                    unlocked = true
                }
            } else if (!unlocked && !prompting) authenticate()
        }
    }

    override fun onPause() {
        unlocked = false
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        super.onPause()
    }

    private fun authenticate() {
        if (prompting) return
        val authenticators =
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (
            BiometricManager.from(this).canAuthenticate(authenticators) !=
                BiometricManager.BIOMETRIC_SUCCESS
        ) {
            error = "端末の画面ロックを設定してください。認証なしでは開けません。"
            return
        }
        prompting = true
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("ライブ・遠征管理を開く")
                .setAllowedAuthenticators(authenticators)
                .build()
        )
    }
}
