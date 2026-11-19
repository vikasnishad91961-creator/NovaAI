package com.nova.ai

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Msg(
    val text: String,
    val isUser: Boolean,
    val time: Long = System.currentTimeMillis()
)

class MainActivity : ComponentActivity() {

    private lateinit var assistant: Assistant
    private var voice: Voice? = null

    // UI state (activity level, taaki callbacks safe rahein)
    private val messages = mutableStateListOf<Msg>()
    private var input by mutableStateOf("")
    private var thinking by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var speaking by mutableStateOf(false)
    private var muted by mutableStateOf(false)

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("nexora_chat", Context.MODE_PRIVATE)
    }

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                askAI("इस तस्वीर को ध्यान से देखकर समझाओ।", bitmap)
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
        WindowCompat.setDecorFitsSystemWindows(window, false)

        permissions.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CONTACTS
            )
        )

        assistant = Assistant(this)

        voice = Voice(
            this,
            onText = { result -> runOnUiThread { askAI(result) } },
            onListening = { active -> runOnUiThread { listening = active } },
            onSpeaking = { active -> runOnUiThread { speaking = active } }
        )

        loadMessages()

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var splash by remember { mutableStateOf(true) }

                LaunchedEffect(Unit) {
                    delay(2800)
                    splash = false
                }

                if (splash) SplashScreen() else ChatScreen()
            }
        }
    }

    override fun onDestroy() {
        voice?.destroy()
        super.onDestroy()
    }

    // ---------------- Logic ----------------

    private fun saveMessages() {
        try {
            val array = JSONArray()
            messages.takeLast(100).forEach { msg ->
                array.put(
                    JSONObject()
                        .put("text", msg.text)
                        .put("user", msg.isUser)
                        .put("time", msg.time)
                )
            }
            prefs.edit().putString("messages", array.toString()).apply()
        } catch (e: Exception) {
            // save fail ho to app crash na ho
        }
    }

    private fun loadMessages() {
        try {
            val array = JSONArray(prefs.getString("messages", "[]"))
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                messages.add(
                    Msg(
                        text = obj.getString("text"),
                        isUser = obj.getBoolean("user"),
                        time = obj.optLong("time", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            messages.clear()
        }
    }

    private fun addMessage(msg: Msg) {
        messages.add(msg)
        saveMessages()
    }

    private fun clearChat() {
        messages.clear()
        saveMessages()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("NEXORA AI", text))
        Toast.makeText(this, "Message copied", Toast.LENGTH_SHORT).show()
    }

    private fun askAI(question: String, bitmap: Bitmap? = null) {
        val prompt = question.trim()
        if ((prompt.isEmpty() && bitmap == null) || thinking) return

        if (prompt.isNotEmpty()) addMessage(Msg(prompt, true))
        input = ""
        thinking = true

        lifecycleScope.launch {
            try {
                // Photo ho to pehle Vision se description banao, phir Assistant ko do
                val imageCtx = bitmap?.let { Vision.describe(it) }
                val answer = assistant.handle(prompt, imageCtx)
                addMessage(Msg(answer, false))
                if (!muted) voice?.speak(answer)
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

    private fun toggleMic() {
        if (listening) {
            voice?.stopListening()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            permissions.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            Toast.makeText(this, "Mic permission allow kijiye", Toast.LENGTH_SHORT).show()
            return
        }
        voice?.listen()
    }

    private fun openCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            cameraLauncher.launch(null)
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    // ---------------- UI ----------------

    @Composable
    private fun SplashScreen() {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF05071A), Color(0xFF111B42), Color(0xFF05071A))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Orb(active = true, modifier = Modifier.size(150.dp))

                Spacer(Modifier.height(25.dp))

                Text(
                    "NEXORA AI",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 6.sp
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "ONE BRAIN. INFINITE INTELLIGENCE. 🚀",
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

                Text("INITIALIZING AI...", color = Color.LightGray, fontSize = 11.sp)
            }
        }
    }

    @Composable
    private fun ChatScreen() {
        val listState = rememberLazyListState()

        LaunchedEffect(messages.size, thinking) {
            val count = messages.size + if (thinking) 1 else 0
            if (count > 0) listState.animateScrollToItem(count - 1)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF070A18))
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
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
                    active = thinking || listening || speaking,
                    modifier = Modifier.size(44.dp)
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
                        when {
                            listening -> "Sun raha hoon…"
                            thinking -> "Thinking…"
                            speaking -> "Bol raha hoon…"
                            else -> "AI Assistant"
                        },
                        color = Color(0xFF7DF9FF),
                        fontSize = 11.sp
                    )
                }

                Text(
                    if (muted) "🔇" else "🔊",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { muted = !muted }
                        .padding(8.dp)
                )

                Text(
                    "🗑",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clickable { clearChat() }
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
                        Orb(active = false, modifier = Modifier.size(150.dp))

                        Spacer(Modifier.height(20.dp))

                        Text(
                            "Namaste! Main NEXORA AI hoon",
                            color = Color.White,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(8.dp))

                        Text(
                            "Mic dabao ya kuch bhi poochho.",
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
                        MessageBubble(msg) { copyToClipboard(msg.text) }
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
                        Text(item, color = Color(0xFF7DF9FF), fontSize = 11.sp)
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
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    cursorBrush = SolidColor(Color(0xFF00E5FF)),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { askAI(input) }),
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
                        .clickable { toggleMic() }
                        .padding(6.dp)
                )

                Text(
                    "➤",
                    color = Color(0xFF00E5FF),
                    fontSize = 25.sp,
                    modifier = Modifier
                        .clickable { askAI(input) }
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

    @Composable
    private fun MessageBubble(msg: Msg, onCopy: () -> Unit) {
        val time = remember(msg.time) {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.time))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
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
                        if (msg.isUser) Color(0xFF244D78) else Color(0xFF17213A)
                    )
                    .pointer