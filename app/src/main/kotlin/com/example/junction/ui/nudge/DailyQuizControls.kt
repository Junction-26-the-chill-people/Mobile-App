package com.example.junction.ui.nudge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.junction.dailyquiz.DailyQuizTimes
import com.example.junction.dailyquiz.QuizPeriod

@Composable
fun DailyQuizControls(
    enabled: Boolean,
    times: DailyQuizTimes,
    appOpenGate: Boolean,
    usageAccess: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (QuizPeriod) -> Unit,
    onAppOpenGateChange: (Boolean) -> Unit,
) {
    Column(Modifier.widthIn(max = 420.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Daily quiz visits", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Text("A morning and an evening check-in.", color = Color(0xFF82776D), fontSize = 12.sp)
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onTimeChange(QuizPeriod.MORNING) }, modifier = Modifier.weight(1f)) {
                Text("Morning ${times.morning.display()}", fontSize = 13.sp)
            }
            OutlinedButton(onClick = { onTimeChange(QuizPeriod.EVENING) }, modifier = Modifier.weight(1f)) {
                Text("Evening ${times.evening.display()}", fontSize = 13.sp)
            }
        }
        Text("Visits wait until your phone has been unlocked for 15 seconds. Locking the phone saves a pending visit for later.",
            color = Color(0xFF82776D), fontSize = 12.sp, lineHeight = 18.sp)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Also wait for an app to open", fontSize = 13.sp)
                Text(if (usageAccess) "Optional extra timing rule" else "Optional · needs Usage Access", color = Color(0xFF82776D), fontSize = 11.sp)
            }
            Switch(checked = appOpenGate, onCheckedChange = onAppOpenGateChange)
        }
        if (appOpenGate) {
            TextButton(onClick = { onAppOpenGateChange(false) }) { Text("Use screen time only", fontSize = 12.sp) }
        }
    }
}
