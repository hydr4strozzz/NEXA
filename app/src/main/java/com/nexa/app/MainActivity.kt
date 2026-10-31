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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

val OrangeAccent = Color(0xFFFF7A00)
val BgColor = Color(0xFF050505)
val InputBg = Color(0xFF1E1E1E)
val UserBubbleColor = Color(0xFF2C2C2E)
val AiBubbleColor = Color(0xE61A1A1A) // Slight transparency to see flames

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color.Transparent,
                    surface = Color.Transparent,
                    primary = OrangeAccent
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
    val listState = rememberLazyListState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@rememberLauncherForActivityResult
            val asText = try { String(bytes, Charsets.UTF_8) } catch (_: Exception) { "" }
            if (asText.isNotBlank() && asText.none { it.code in 0..8 || it.code in 14..31 }) {
                vm.send("", null, asText)
            } else {
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                vm.send("", b64, null)
            }
        } catch (_: Exception) { }
    }

    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.lastIndex)
    }

    Box(modifier = Modifier.fillMaxSize().background(BgColor)) {
        // Background Flame Image
        AsyncImage(
            model = "https://images.unsplash.com/photo-1541701494587-cb58502866ab?q=80&w=1000&auto=format&fit=crop",
            contentDescription = "Flame Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.7f
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(OrangeAccent, CircleShape)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, contentDescription = "User", tint = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("User", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.LightGray,
                    modifier = Modifier.size(28.dp)
                )
            }

            // Chat Area
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(vm.messages.size) { i -> Bubble(vm.messages[i]) }
            }

            // Bottom Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(InputBg, RoundedCornerShape(30.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text("Message...", color = Color.Gray, fontSize = 16.sp) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        maxLines = 4
                    )
                    IconButton(onClick = { filePicker.launch("*/*") }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = Color.Gray)
                    }
                    IconButton(onClick = { /* Mic logic (Optional) */ }) {
                        Icon(Icons.Default.Mic, contentDescription = "Mic", tint = Color.Gray)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                // Send Button
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(OrangeAccent, CircleShape)
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            val t = input.trim()
                            if (t.isNotEmpty()) {
                                vm.send(t, null, null)
                                input = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
            }
        }
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
                .widthIn(max = 300.dp)
                .background(
                    if (isUser) UserBubbleColor else AiBubbleColor,
                    RoundedCornerShape(20.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                if (m.content.isNotBlank()) {
                    Text(
                        text = if (m.pending && m.content.isEmpty()) "..." else m.content,
                        color = Color.White,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                } else if (m.pending) {
                    Text("...", color = Color.Gray)
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
    }
}
