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
