package app.kurenai.ui.onboarding

import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun AppLockScreen(biometricEnabled: Boolean = false, onUnlocked: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var error by remember { mutableStateOf<String?>(null) }
    val credentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) onUnlocked() else error = "Kurenai stays locked."
    }

    fun prompt() {
        if (activity == null) {
            error = "App lock needs a FragmentActivity host."
            return
        }
        val manager = BiometricManager.from(context)
        val can = if (biometricEnabled) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
        }
        if (can == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        error = message.toString()
                    }
                },
            )
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Kurenai")
                    .setSubtitle("Your shelf is private")
                    .setNegativeButtonText("Use device PIN")
                    .build(),
            )
        } else {
            val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            val intent = keyguard.createConfirmDeviceCredentialIntent("Unlock Kurenai", "Your shelf is private")
            if (intent != null) credentialLauncher.launch(intent) else error = "No device authentication is configured."
        }
    }

    LaunchedEffect(Unit) { prompt() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        ) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(76.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Lock, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "Kurenai is locked",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp),
                )
                Text(
                    "Your shelf, history and downloads stay private.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                Button(onClick = { prompt() }, modifier = Modifier.padding(top = 20.dp)) { Text("Unlock") }
                OutlinedButton(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Close Kurenai") }
            }
        }
    }
}
