package app.kurenai.ui.challenge

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.kurenai.core.designsystem.theme.KurenaiTheme
import app.kurenai.core.network.challenge.InteractiveChallengeManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ChallengeActivity : ComponentActivity() {
    @Inject lateinit var manager: InteractiveChallengeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra("challengeId") ?: run { finish(); return }
        val url = intent.getStringExtra("url").orEmpty()
        val host = intent.getStringExtra("host").orEmpty()
        val userAgent = intent.getStringExtra("userAgent").orEmpty()
        setContent {
            KurenaiTheme { ChallengePage(url, host, userAgent, onDone = {
                manager.complete(id, true)
                finish()
            }, onCancel = {
                manager.complete(id, false)
                finish()
            }) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ChallengePage(url: String, host: String, userAgent: String, onDone: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text("$host needs a quick check", style = MaterialTheme.typography.titleMedium)
        Text(
            "This is a real browser inside Kurenai. Complete the verification once; the original request will continue automatically.",
            style = MaterialTheme.typography.bodySmall,
        )
        AndroidView(
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = userAgent
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = WebViewClient()
                    loadUrl(url)
                }
            },
        )
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End, modifier = Modifier.fillMaxSize(0.1f)) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onDone) { Text("I'm done") }
        }
    }
}
