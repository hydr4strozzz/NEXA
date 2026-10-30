#!/usr/bin/env bash
set -e
mkdir -p app/src/main/java/com/nexa/app

# ─────────────────────────────────────────────────────────────
cat > app/src/main/java/com/nexa/app/Providers.kt <<'EOF'
package com.nexa.app

data class Provider(
    val name: String,
    val baseUrl: String,
    val model: String,
    val visionModel: String?
)

object Providers {
    fun build(groq: String, deepseek: String): Map<String, Provider> = mapOf(
        "groq" to Provider(
            name = "groq",
            baseUrl = "https://api.groq.com/openai/v1",
            model = "llama-3.3-70b-versatile",
            visionModel = "meta-llama/llama-4-scout-17b-16e-instruct"
        ),
        "deepseek" to Provider(
            name = "deepseek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            visionModel = null
        )
    )

    fun chain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq", "deepseek").filter { keyAvailable(it) }
    }

    fun visionChain(keyAvailable: (String) -> Boolean): List<String> {
        return listOf("groq").filter { keyAvailable(it) }
    }
}
EOF

# ─────────────────────────────────────────────────────────────
cat > app/src/main/java/com/nexa/app/ChatViewModel.kt <<'EOF'
package com.nexa.app

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import org.json.JSONObject

data class Message(
    val role: String,
    val content: String,
    val imageB64: String? = null,
    val imageUrl: String? = null,
    val pending: Boolean = false,
    val provider: String? = null
)

class ChatViewModel(app: Application) : AndroidViewModel(app) {
    val messages = mutableStateListOf<Message>()
    private val api = ApiClient()
    private val main = Handler(Looper.getMainLooper())

    private val bundledGroq: String
    private val bundledDeepseek: String

    init {
        var g = ""; var d = ""
        try {
            val json = app.assets.open("config.json").bufferedReader().use { it.readText() }
            val j = JSONObject(json)
            g = j.optString("groq", ""); d = j.optString("deepseek", "")
        } catch (_: Exception) { }
        bundledGroq = g; bundledDeepseek = d
    }

    private fun groqKey() = bundledGroq
    private fun deepseekKey() = bundledDeepseek

    private fun providerMap() = Providers.build(groqKey(), deepseekKey())

    private fun keyAvailable(name: String): Boolean = when (name) {
        "groq" -> groqKey().isNotBlank()
        "deepseek" -> deepseekKey().isNotBlank()
        else -> false
    }

    private fun keyFor(name: String): String = when (name) {
        "groq" -> groqKey()
        "deepseek" -> deepseekKey()
        else -> ""
    }

    fun send(text: String, imageB64: String?, fileText: String?) {
        if (text.isBlank() && imageB64 == null && fileText == null) return

        if (ImageGen.isImageRequest(text) && imageB64 == null) {
            val prompt = ImageGen.extractPrompt(text)
            val url = ImageGen.pollinations(prompt)
            messages.add(Message("user", text))
            messages.add(Message("assistant", "Generating image: $prompt", imageUrl = url, provider = "ImageGen"))
            return
        }

        val userText = buildString {
            if (fileText != null && fileText.isNotBlank()) {
                append("[file content]\n").append(fileText.take(20000)).append("\n[/file]\n\n")
            }
            append(text)
        }.trim().ifBlank { text }

        messages.add(Message("user", text.ifBlank { "[file]" }, imageB64))
        messages.add(Message("assistant", "", pending = true))
        val idx = messages.lastIndex

        val chain = if (imageB64 != null) Providers.visionChain(::keyAvailable)
                    else Providers.chain(::keyAvailable)

        if (chain.isEmpty()) {
            messages[idx] = messages[idx].copy(content = "Error: API keys are not configured in this build.", pending = false)
            return
        }

        val history = messages.filter { !it.pending }.mapIndexed { i, m ->
            if (i == messages.size - 2 && userText.isNotBlank())
                WireMessage(m.role, if (m.role == "user") userText else m.content, m.imageB64)
            else
                WireMessage(m.role, m.content, m.imageB64)
        }

        tryChain(chain, 0, history, idx)
    }

    private fun tryChain(chain: List<String>, pos: Int, history: List<WireMessage>, idx: Int) {
        if (pos >= chain.size) {
            main.post {
                val cur = messages[idx]
                if (cur.content.isBlank()) messages[idx] = cur.copy(content = "[all providers failed]", pending = false)
                else messages[idx] = cur.copy(pending = false)
            }
            return
        }
        val name = chain[pos]
        val p = providerMap()[name] ?: run { tryChain(chain, pos + 1, history, idx); return }
        val apiKey = keyFor(name)
        if (apiKey.isBlank()) { tryChain(chain, pos + 1, history, idx); return }

        val hasImage = history.lastOrNull()?.imageB64 != null
        val model = if (hasImage && p.visionModel != null) p.visionModel else p.model

        var gotToken = false

        api.streamChat(
            baseUrl = p.baseUrl,
            apiKey = apiKey,
            model = model,
            system = Persona.systemPrompt,
            history = history,
            onToken = { tok ->
                gotToken = true
                main.post {
                    val cur = messages[idx]
                    messages[idx] = cur.copy(content = cur.content + tok, pending = false, provider = name)
                }
            },
            onDone = { main.post { messages[idx] = messages[idx].copy(pending = false) } },
            onError = {
                if (!gotToken) {
                    main.post { messages[idx] = messages[idx].copy(content = "", provider = null) }
                    tryChain(chain, pos + 1, history, idx)
                } else {
                    main.post { messages[idx] = messages[idx].copy(pending = false) }
                }
            }
        )
    }
}
EOF

# ─────────────────────────────────────────────────────────────
cat > app/src/main/java/com/nexa/app/MainActivity.kt <<'EOF'
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

// Professional Dark Theme Colors
val BgColor = Color(0xFF0F0F11)
val SurfaceColor = Color(0xFF1C1C1E)
val UserBubbleColor = Color(0xFF6D28D9) // Deep Purple
val AiBubbleColor = Color(0xFF2C2C2E)   // Dark Gray
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BgColor,
                    surface = SurfaceColor,
                    primary = UserBubbleColor,
                    onBackground = TextPrimary,
                    onSurface = TextPrimary
                )
            ) { NexaApp() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexaApp(vm: ChatViewModel = viewModel()) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
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
                title = {
                    Column {
                        Text("NEXA", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = TextPrimary)
                        Text("Unrestricted AI", fontSize = 12.sp, color = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SurfaceColor)
            )
        },
        containerColor = BgColor,
        bottomBar = {
            Column(Modifier.background(SurfaceColor).padding(8.dp)) {
                if (pendingImage != null || pendingFileText != null || imageMode) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        if (pendingImage != null) Text("Image attached", color = TextSecondary, fontSize = 12.sp)
                        if (pendingFileText != null) Text("File attached", color = TextSecondary, fontSize = 12.sp)
                        if (imageMode) Text("Image Gen Active", color = Color(0xFF10B981), fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { pendingImage = null; pendingFileText = null; imageMode = false }) {
                            Text("Clear", color = Color(0xFFEF4444), fontSize = 12.sp)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Text("FILE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                    IconButton(onClick = { imgPicker.launch("image/*") }) {
                        Text("IMG", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    }
                    IconButton(onClick = { imageMode = !imageMode }) {
                        Text("GEN", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if(imageMode) Color(0xFF10B981) else TextSecondary)
                    }
                    
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        placeholder = { Text(if (imageMode) "Describe the image..." else "Message NEXA...", color = TextSecondary) },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BgColor,
                            unfocusedContainerColor = BgColor,
                            focusedBorderColor = UserBubbleColor,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    
                    IconButton(
                        onClick = {
                            val t = input.trim()
                            if (imageMode && t.isNotBlank()) {
                                vm.send("/img $t", null, null)
                            } else if (t.isNotEmpty() || pendingImage != null || pendingFileText != null) {
                                vm.send(t, pendingImage, pendingFileText)
                            } else return@IconButton
                            input = ""; pendingImage = null; pendingFileText = null; imageMode = false
                        },
                        modifier = Modifier.background(UserBubbleColor, RoundedCornerShape(50)).size(48.dp)
                    ) {
                        Text(">", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(pad).fillMaxSize().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
        ) { items(vm.messages.size) { i -> Bubble(vm.messages[i]) } }
    }
}

@Composable
fun Bubble(m: Message) {
    val isUser = m.role == "user"
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .background(
                    if (isUser) UserBubbleColor else AiBubbleColor,
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isUser) 16.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 16.dp
                    )
                )
                .padding(12.dp)
        ) {
            Column {
                if (m.content.isNotBlank()) {
                    Text(
                        text = if (m.pending && m.content.isEmpty()) "Thinking..." else m.content,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        lineHeight = 22.sp
                    )
                } else if (m.pending) {
                    Text("Thinking...", color = TextSecondary, fontSize = 15.sp)
                }
                
                m.imageUrl?.let { url ->
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = "generated",
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).background(Color.Black, RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }
        if (!isUser && m.provider != null) {
            Text(
                text = m.provider ?: "",
                color = TextSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            )
        }
    }
}
EOF

echo "patched: UI + logic"
