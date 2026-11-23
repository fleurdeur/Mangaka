package app.kurenai

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kurenai.core.designsystem.theme.KurenaiTheme
import app.kurenai.ui.KurenaiApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: app.kurenai.ui.AppShellViewModel = hiltViewModel()
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val ready by settingsViewModel.ready.collectAsStateWithLifecycle()
            KurenaiTheme(settings.theme) {
                if (!ready) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.background,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.background,
                                ),
                            ),
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                        KurenaiApp(settingsViewModel = settingsViewModel)
                    }
                }
            }
        }
    }
}
