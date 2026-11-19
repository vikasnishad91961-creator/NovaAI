
package com.nova.ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Msg(
    val text: String,
    val isUser: Boolean,
    val time: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private lateinit var assistant: Assistant
    private var voice: Voice? = null

    private var voiceTextCallback: ((String) -> Unit)? = null
    private var listeningCallback: ((Boolean) -> Unit)? = null
    private var imageCallback: ((Bitmap) -> Unit)? = null

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imageCallback?.invoke(bitmap)
            }
        }

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                cameraLauncher.launch(null)
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        assistant = Assistant(this)

        voice = Voice(
            this,
            onText = { result ->
                runOnUiThread {
                    voiceTextCallback?.invoke(result)
                }
            },
            onListening = { active ->
                runOnUiThread {
                    listeningCallback?.invoke(active)
                }
            },
            onSpeaking = { }
        )

        setContent {
            MaterialTheme {
                var splash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(2800)
                    splash = false
                }

                if (splash) {
                    SplashScreen()
                } else {
                    ChatScreen()
                }
            }
        }
    }

    private fun openCamera() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(null)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        voice?.destroy()
        super.onDestroy()
    }

    @Composable
    private fun SplashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF05071A),
                            Color(0xFF111B42),
                            Color(0xFF05071A)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Orb(
                    active = true,
                    modifier = Modifier.size(130.dp)
                )

                Spacer(Modifier.height(25.dp))

                Text(
                    "NEXORA AI",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "ONE BRAIN. INFINITE INTELLIGENCE.",
                    color = Color(0xFF7DF9FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(30.dp))

                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    "INITIALIZING AI...",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
            }
        }
    }

    @Composable
    private fun ChatScreen() {
        val prefs = remember {
            getSharedPreferences("nexora_chat", Context.MODE_PRIVATE)
        }

        val scope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        var messages by remember { mutableStateOf(loadMessages(prefs)) }
        var input by remember { mutableStateOf("") }
        var thinking by remember { mutableStateOf(false) }
        var listening by remember { mutableStateOf(false) }

        fun saveMessages() {
            val array = JSONArray()

            messages.forEach { msg ->
                val obj = JSONObject()
                obj.put("text", msg.text)
                obj.put("user", msg.isUser)
                obj.put("time", msg.time)
                array.put(obj)
            }

            prefs.edit().putString("messages", array.toString()).apply()
        }

        fun addMessage(msg: Msg) {
            messages = messages + msg
            saveMessages()
        }

        fun askAI(question: String, bitmap: Bitmap? = null) {
            val prompt = question.trim()
            if ((prompt.isEmpty() && bitmap == null) || thinking) return

            if (prompt.isNotEmpty()) {
                addMessage(Msg(prompt, true))
            }

            input = ""
            thinking = true

            scope.launch {
                try {
                    val answer = assistant.handle(prompt, bitmap)
                    addMessage(Msg(answer, false))
                } catch (e: Exception) {
                    addMessage(
                        Msg(
                            "माफ़ करना, अभी जवाब नहीं मिल पाया। दोबारा कोशिश करो।\n\n${e.message ?: "Unknown error"}",
                            false
                        )
                    )
                } finally {
                    thinking = false
                }
            }
        }

        voiceTextCallback = { spoken ->
            input = spoken
        }

        listeningCallback = { active ->
            listening = active
        }

        imageCallback = { bitmap ->
            askAI("इस तस्वीर को ध्यान से देखकर समझाओ।", bitmap)
        }

        LaunchedEffect(messages.size, thinking) {
            if (messages.isNotEmpty()) {
                listState.animateScrollToItem(
                    if (thinking) messages.size else messages.lastIndex
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A18))
        ) {
            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10162E))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Orb(
                    active = thinking,
                    modifier = Modifier.size(43.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        "NEXORA AI",
                        color = Color.White,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        if (thinking) "Thinking..." else "AI Assistant",
                        color = Color(0xFF7DF9FF),
                        fontSize = 11.sp
                    )
                }

                Text(
                    "🗑",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable {
                            messages = emptyList()
                            saveMessages()
                        }
                        .padding(8.dp)
                )
            }

            // CHAT AREA
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Orb(
                            active = false,
                            modifier = Modifier.size(105.dp)
                        )

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "Hello! I'm NEXORA AI",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Ask me anything.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        MessageBubble(msg) {
                            val clipboard =
                                getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("NEXORA AI", msg.text)
                            )

                            Toast.makeText(
                                this@MainActivity,
                                "Message copied",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    if (thinking) {
                        item {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(17.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )

                                Spacer(Modifier.width(10.dp))

                                Text(
                                    "NEXORA is thinking...",
                                    color = Color(0xFF7DF9FF),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            // QUICK PROMPTS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Explain", "Ideas", "Translate").forEach { item ->
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFF17213E))
                            .clickable {
                                input = when (item) {
                                    "Explain" -> "Explain this topic simply: "
                                    "Ideas" -> "Give me creative ideas about "
                                    else -> "Translate into Hindi: "
                                }
                            }
                            .padding(horizontal = 13.dp, vertical = 8.dp)
                    ) {
                        Text(
                            item,
                            color = Color(0xFF7DF9FF),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // MESSAGE INPUT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10162E))
                    .padding(9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "📷",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { openCamera() }
                        .padding(6.dp)
                )

                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xFF1A2340))
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 4,
                    decorationBox = { innerTextField ->
                        Box {
                            if (input.isEmpty()) {
                                Text(
                                    "Message NEXORA...",
                                    color = Color.Gray,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                Text(
                    if (listening) "🔴" else "🎤",
                    fontSize = 21.sp,
                    modifier = Modifier
                        .clickable {
                            if (listening) {
                                voice?.stopListening()
                            } else {
                                voice?.listen()
                            }
                        }
                        .padding(6.dp)
                )

                Text(
                    "➤",
                    color = Color(0xFF00E5FF),
                    fontSize = 25.sp,
                    modifier = Modifier
                        .clickable {
                            askAI(input)
                        }
                        .padding(6.dp)
                )
            }

            Text(
                "NEXORA AI can make mistakes. Verify important information.",
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF10162E))
                    .padding(bottom = 7.dp),
                color = Color.Gray,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    private fun loadMessages(
        prefs: android.content.SharedPreferences
    ): List<Msg> {
        return try {
            val array = JSONArray(
                prefs.getString("messages", "[]")
            )

            List(array.length()) { index ->
                val obj = array.getJSONObject(index)

                Msg(
                    text = obj.getString("text"),
                    isUser = obj.getBoolean("user"),
                    time = obj.optLong("time", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Composable
    private fun MessageBubble(
        msg: Msg,
        onCopy: () -> Unit
    ) {
        val time = remember(msg.time) {
            SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
            ).format(Date(msg.time))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (msg.isUser) {
                Arrangement.End
            } else {
                Arrangement.Start
            }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .padding(vertical = 5.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 18.dp,
                            topEnd = 18.dp,
                            bottomStart = if (msg.isUser) 18.dp else 4.dp,
                            bottomEnd = if (msg.isUser) 4.dp else 18.dp
                        )
                    )
                    .background(
                        if (msg.isUser) {
                            Color(0xFF244D78)
                        } else {
                            Color(0xFF17213A)
                        }
                    )
                    .pointerInput(msg.text) {
                        detectTapGestures(
                            onLongPress = { onCopy() }
                        )
                    }
                    .padding(13.dp)
            ) {
                Text(
                    msg.text,
                    color = Color.White,
                    fontSize = 14.sp
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    time,
                    color = Color.LightGray,
                    fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
fun Orb(
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "nexora_orb")

    val angle by transition.