package com.example.junction.ui.nudge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.junction.catnudge.visual.CatSceneView

private val Ink = Color(0xFF3D3834)
private val Muted = Color(0xFF82776D)
private val Cream = Color(0xFFFFFAF1)
private val Peach = Color(0xFFF0B69C)

@Composable
private fun CatTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = Ink, onPrimary = Cream, background = Cream,
        onBackground = Ink, surface = Color.White, onSurface = Ink
    ), content = content)
}

@Composable
fun NudgeHomeScreen(
    overlayAllowed: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onPreviewQuestions: () -> Unit,
    notificationsAllowed: Boolean = true,
    onEnableNotifications: () -> Unit = {},
    dailySettings: @Composable () -> Unit = {},
) = CatTheme {
    Surface(color = Cream, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("junction", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("A LITTLE NUDGE", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(36.dp))
            Box(Modifier.size(224.dp).background(Color(0xFFF5EBDD), CircleShape), contentAlignment = Alignment.Center) {
                RestingCat(Modifier.size(220.dp, 150.dp))
            }
            Spacer(Modifier.height(26.dp))
            Text("Small paws.\nA little pause.", color = Ink, fontFamily = FontFamily.Serif,
                fontSize = 38.sp, lineHeight = 43.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(15.dp))
            Text("A tiny visitor with a question for you.", color = Muted, fontSize = 15.sp,
                textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Column(Modifier.widthIn(max = 420.dp).fillMaxWidth()
                .border(1.dp, Color(0xFFE6DCCE), RoundedCornerShape(22.dp))
                .background(Color(0xFFFFFDF8), RoundedCornerShape(22.dp)).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Box(Modifier.size(8.dp).background(if (overlayAllowed) Color(0xFF8AA48C) else Peach, CircleShape))
                    Text(if (overlayAllowed) "Ready for a visit" else "Let the cat visit", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(9.dp))
                Text(if (overlayAllowed) "The cat can appear over your other apps. Send a visit here, or trigger one with ADB."
                else "Allow Junction to display over other apps. Then our little courier can bring you a question wherever you are.",
                    color = Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onSend, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Ink)) {
                    Text(if (overlayAllowed) "Send a little nudge" else "Allow & send a nudge", fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onStop) { Text("Stop visit", color = Muted) }
                TextButton(onClick = onPreviewQuestions) { Text("Future space  ↗", color = Muted) }
            }
            if (!notificationsAllowed) {
                TextButton(onClick = onEnableNotifications) {
                    Text("Enable visit notifications", color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            dailySettings()
            Spacer(Modifier.height(20.dp))
            Text("A box, a question, and a very patient cat.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun RestingCat(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember(context) { CatSceneView(context).apply { showResting() } }
    DisposableEffect(view) { onDispose { view.stop() } }
    AndroidView(factory = { view }, modifier = modifier)
}

@Composable
fun ReservedScreen(onBack: () -> Unit, period: String = "preview") = CatTheme {
    Surface(Modifier.fillMaxSize(), color = Cream) {
        Box(Modifier.safeDrawingPadding().padding(24.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
                Text("← Back", color = Muted)
            }
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                RestingCat(Modifier.size(180.dp, 120.dp))
                Spacer(Modifier.height(24.dp))
                Text(when (period) {
                    "morning" -> "Morning daily quiz"
                    "evening" -> "Evening daily quiz"
                    else -> "Daily quiz"
                }, color = Muted, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("<reserved for future>", fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp, color = Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text("A little space for what comes next.", color = Muted, fontSize = 13.sp)
            }
        }
    }
}
