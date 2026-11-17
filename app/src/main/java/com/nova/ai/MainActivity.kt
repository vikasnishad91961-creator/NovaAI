package com.nova.ai

import android.Manifest
import android.os.Bundle
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {
    private lateinit var voice: Voice
    private lateinit var nova: Assistant
    private val msgs = mutableStateListOf<Pair<Boolean, String>>()
    private var listening by mutableStateOf(false)
    private var speaking by mutableStateOf(false)
    private var imageCtx: String? = null

    private val perms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        if (bmp != null) lifecycleScope.launch {
            imageCtx = Vision.describe(bmp)
            say("Photo mil gayi. Ab is ke baare mein sawal poochiye.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS))
        voice = Voice(this, onText = { process(it) }, onListening = { listening = it }, onSpeaking = { speaking = it })
        nova = Assistant(this)
        msgs.add(false to "Namaste! Main Nova hoon. Mic dabao aur bolo.")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { Screen() }
        }
    }

    override fun onDestroy() { voice.destroy(); super.onDestroy() }

    private fun say(text: String) { msgs.add(false to text); voice.speak(text) }

    private fun process(text: String) {
        if (text.isBlank()) return
        msgs.add(true to text)
        lifecycleScope.launch {
            val reply = nova.handle(text, imageCtx)
            imageCtx = null
            say(reply)
        }
    }

    @Composable
    private fun Screen() {
        var input by remember { mutableStateOf("") }
        val list = rememberLazyListState()
        LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.lastIndex) }

        Column(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF05071A), Color(0xFF0B1030))))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NOVA", fontSize = 22.sp, color = Color(0xFF7DF9FF), letterSpacing = 8.sp)
            Orb(active = listening || speaking)
            Text(
                when { listening -> "Sun raha hoon…"; speaking -> "Bol raha hoon…"; else -> "Tayyar" },
                color = Color(0xFF9AA4D6), fontSize = 13.sp
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), state = list,
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(msgs) { (user, text) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
                        Box(
                            Modifier.widthIn(max = 300.dp)
                                .background(if (user) Color(0xFF2B3A8C) else Color(0xFF151B45), RoundedCornerShape(14.dp))
                                .padding(10.dp)
                        ) { Text(text, color = Color.White, fontSize = 15.sp) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                    placeholder = { Text("Yahan likho…") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { process(input); input = "" })
                )
                Button(onClick = { camera.launch(null) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) { Text("📷") }
                Button(
                    onClick = { if (listening) voice.stopListening() else voice.listen() },
                    colors = ButtonDefaults.buttonColors(containerColor = if (listening) Color(0xFFD32F6B) else Color(0xFF00B8D4)),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
                ) { Text(if (listening) "⏹" else "🎤") }
            }
        }
    }
}

/** Ghoomta hua 3D golaa: Fibonacci sphere ke points + orbit rings. */
@Composable
fun Orb(active: Boolean) {
    val t = rememberInfiniteTransition(label = "orb")
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(16000, easing = LinearEasing)), label = "a")
    val pulse by t.animateFloat(0.96f, 1.06f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val pts = remember {
        val n = 450
        FloatArray(n * 3).also { a ->
            val ga = PI * (3 - sqrt(5.0))
            for (i in 0 until n) {
                val y = 1 - 2f * i / (n - 1)
                val r = sqrt(1 - y * y)
                val th = ga * i
                a[i * 3] = (cos(th) * r).toFloat(); a[i * 3 + 1] = y; a[i * 3 + 2] = (sin(th) * r).toFloat()
            }
        }
    }
    Canvas(Modifier.size(240.dp)) {
        val c = center
        val half = size.minDimension / 2
        val r = half * 0.72f * (if (active) pulse else 1f)
        drawCircle(Brush.radialGradient(listOf(Color(0x6600E5FF), Color.Transparent), c, half), half, c)
        drawCircle(Brush.radialGradient(listOf(Color(0xFF1B2A6B), Color(0xFF05071A)), c - Offset(r * .3f, r * .3f), r * 1.3f), r, c)
        val a = Math.toRadians(angle.toDouble()); val ca = cos(a).toFloat(); val sa = sin(a).toFloat()
        val tilt = 0.45f; val ct = cos(tilt); val st = sin(tilt)
        for (i in 0 until pts.size / 3) {
            val x = pts[i * 3]; val y = pts[i * 3 + 1]; val z = pts[i * 3 + 2]
            val x1 = x * ca + z * sa; val z1 = -x * sa + z * ca
            val y2 = y * ct - z1 * st; val z2 = y * st + z1 * ct
            val d = (z2 + 1f) / 2f
            val col = if (i % 3 == 0) Color(0xFFB388FF) else Color(0xFF7DF9FF)
            drawCircle(col.copy(alpha = 0.15f + 0.85f * d), 1.2f + 2.6f * d, Offset(c.x + x1 * r, c.y + y2 * r))
        }
        val ringW = half * 1.7f
        rotate(angle * 2, c) {
            drawOval(Color(0x887DF9FF), Offset(c.x - ringW / 2, c.y - ringW * 0.16f), Size(ringW, ringW * 0.32f), style = Stroke(2.5f))
        }
        rotate(-angle * 1.4f + 60f, c) {
            drawOval(Color(0x88B388FF), Offset(c.x - ringW / 2, c.y - ringW * 0.16f), Size(ringW, ringW * 0.32f), style = Stroke(2f))
        }
    }
}
