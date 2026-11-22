package app.kurenai.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AboutScreen(onBack: () -> Unit) {
    InfoPage("About Kurenai", onBack) {
        Text("Kurenai is an open-source, native Android reader for manga, manhwa, manhua, webtoons, comics and light novels.", style = MaterialTheme.typography.bodyMedium)
        Text("Version 1.0.0", style = MaterialTheme.typography.labelMedium)
        Text(
            "Kurenai hosts no content. Every page is fetched directly from third-party sources chosen by you. The project does not condone piracy; support official releases where available.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "License: GPL-3.0. Parser engine: Kotatsu parsers (GPL-3.0). Built with Kotlin, Jetpack Compose, Room, Hilt and Coil. Fonts: Inter (SIL OFL 1.1) with system CJK fallbacks.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "Privacy: no ads, no analytics, no tracking. Crash reports are opt-in. All recommendations, OCR and translation run locally unless you configure a provider with your own key.",
            style = MaterialTheme.typography.bodySmall,
        )
        Text("DMCA and takedown: see SECURITY.md and docs/DMCA.md in the repository. Kurenai does not host or distribute any manga files.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun HelpScreen(onBack: () -> Unit) {
    InfoPage("Help Center", onBack) {
        Text("Sources", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Kurenai ships the complete Kotatsu parser catalogue. If a source fails, open More → Sources, reset its health, and use Report to export diagnostics. Broken sources are disabled automatically after five consecutive failures.", style = MaterialTheme.typography.bodySmall)
        Text("Cloudflare challenges", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("A hidden WebView solves JavaScript challenges once per domain. If a Turnstile or login is required, Kurenai opens an in-app page; complete it once and the original request continues.", style = MaterialTheme.typography.bodySmall)
        Text("Downloads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("Downloads respect Wi-Fi-only and battery rules. Long-press a chapter for ad-hoc downloads, or use Download next 5 on a series page.", style = MaterialTheme.typography.bodySmall)
        Text("Accessibility", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("TalkBack announces page and bubble content, font scaling is honored, reduced motion disables the animated reader, and switch access works with the standard Compose focus model.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun WhatsNewScreen(onBack: () -> Unit) {
    InfoPage("What's New", onBack) {
        Text("1.0.0 · First light", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("• Complete Kotatsu parser catalogue built in\n• Cloudflare-aware network stack with encrypted cookies\n• Animated Chapters preview: panel detection, rigs, narration and a real-time renderer\n• On-device taste profile, smart updates and cross-source matching\n• Source health dashboard, signed parser update feed and diagnostics export\n• Material 3 Expressive themes for phone, foldable, tablet and desktop mode", style = MaterialTheme.typography.bodyMedium)
        Text("Roadmap", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("• More on-device TTS voice packs\n• Self-hostable sync server reference deployment\n• Panel-by-panel guided view polish\n• Community reading clubs and encrypted lists", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun InfoPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
        item { Column(Modifier.fillMaxWidth()) { content() } }
    }
}

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    viewModel: MoreViewModel = hiltViewModel(),
) {
    var endpoint by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(viewModel.getSecret("ai_endpoint").orEmpty()) }
    var model by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(viewModel.getSecret("ai_model") ?: "gpt-4o-mini") }
    var key by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var enabled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(viewModel.getSecret("ai_enabled").toBoolean()) }

    InfoPage("AI librarian", onBack) {
        Text("The librarian always works locally. Cloud completion is optional and uses an OpenAI-compatible endpoint with your own key.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Endpoint, e.g. https://api.openai.com/v1") }, singleLine = true)
        androidx.compose.material3.OutlinedTextField(value = model, onValueChange = { model = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Model") }, singleLine = true)
        androidx.compose.material3.OutlinedTextField(value = key, onValueChange = { key = it }, modifier = Modifier.fillMaxWidth(), label = { Text("API key (stored encrypted)") }, singleLine = true)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Enable cloud librarian", modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(checked = enabled, onCheckedChange = { enabled = it })
        }
        androidx.compose.material3.Button(onClick = { viewModel.saveAiSettings(endpoint, model, key, enabled) }, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        Text("No chat content is sent unless you ask a question with cloud enabled. No analytics accompany requests.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
