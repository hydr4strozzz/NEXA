package com.nexa.app

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { NexaApp() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaApp(vm: ChatViewModel = viewModel()) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingFileText by remember { mutableStateOf<String?>(null) }
    var imageMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            pendingImage = Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Exception) { }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            val asText = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
            if (asText.isNotBlank() && asText.none { it.code in 0..8 || it.code in 14..31 }) {
                pendingFileText = asText
            } else {
                pendingImage = Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NEXA", fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = { showSettings = !showSettings }) {
                    Text(if (showSettings) "close" else "settings")
                } }
            )
        },
        bottomBar = {
            Column(Modifier.padding(8.dp)) {
                if (pendingImage != null || pendingFileText != null || imageMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (pendingImage != null) Text("image attached", color = Color.DarkGray)
                        if (pendingFileText != null) Text("file attached", color = Color.DarkGray)
                        if (imageMode) Text("image-gen on", color = Color.DarkGray)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { pendingImage = null; pendingFileText = null; imageMode = false }) { Text("clear") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { filePicker.launch("*/*") }) { Text("file") }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { imgPicker.launch("image/*") }) { Text("img") }
                    Spacer(Modifier.width(4.dp))
                    OutlinedButton(onClick = { imageMode = !imageMode }) { Text("gen") }
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(if (imageMode) "describe the image" else "ask anything") },
                        maxLines = 4
                    )
                    Spacer(Modifier.width(6.dp))
                    Button(onClick = {
                        val t = input.trim()
                        if (imageMode && t.isNotBlank()) {
                            vm.send("/img $t", null, null)
                        } else if (t.isNotEmpty() || pendingImage != null || pendingFileText != null) {
                            vm.send(t, pendingImage, pendingFileText)
                        } else return@Button
                        input = ""; pendingImage = null; pendingFileText = null; imageMode = false
                    }) { Text("send") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (showSettings) SettingsPanel(vm)
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) { items(vm.messages.size) { i -> Bubble(vm.messages[i]) } }
        }
    }
}

@Composable
fun Bubble(m: Message) {
    val isUser = m.role == "user"
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
        Box(
            Modifier
                .widthIn(max = 340.dp)
                .background(
                    if (isUser) Color(0xFF1E1E1E) else Color(0xFFEFEFEF),
                    MaterialTheme.shapes.medium
                )
                .padding(10.dp)
        ) {
            Column {
                if (m.content.isNotBlank()) {
                    Text(
                        text = if (m.pending && m.content.isEmpty()) "..." else m.content,
                        color = if (isUser) Color.White else Color.Black
                    )
                } else if (m.pending) {
                    Text("...")
                }
                m.imageUrl?.let { url ->
                    Spacer(Modifier.height(6.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = "generated",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        if (m.imageB64 != null) Text("image", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
        m.provider?.let { Text("via $it", color = Color.Gray, style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
fun SettingsPanel(vm: ChatViewModel) {
    val prefs = vm.prefs()
    var groqKey by remember { mutableStateOf(prefs.getString("groqKey", "").orEmpty()) }
    var deepseekKey by remember { mutableStateOf(prefs.getString("deepseekKey", "").orEmpty()) }
    var orKey by remember { mutableStateOf(prefs.getString("orKey", "").orEmpty()) }
    var forceProvider by remember { mutableStateOf(prefs.getString("forceProvider", "auto") ?: "auto") }

    Column(
        Modifier.fillMaxWidth().heightIn(max = 340.dp)
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text("fallback: ${if (forceProvider == "auto") "groq -> deepseek -> openrouter" else "$forceProvider first, then chain"}", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("auto", "groq", "deepseek", "openrouter").forEach { p ->
                OutlinedButton(onClick = { forceProvider = p }) {
                    Text(p, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        OutlinedTextField(groqKey, { groqKey = it }, label = { Text("groq key (blank = bundled)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(deepseekKey, { deepseekKey = it }, label = { Text("deepseek key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(orKey, { orKey = it }, label = { Text("openrouter key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(onClick = {
            prefs.edit()
                .putString("groqKey", groqKey.trim())
                .putString("deepseekKey", deepseekKey.trim())
                .putString("orKey", orKey.trim())
                .putString("forceProvider", forceProvider)
                .apply()
        }) { Text("save") }
    }
}
