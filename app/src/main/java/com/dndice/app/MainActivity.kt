package com.dndice.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ── Data models ──────────────────────────────────────────

data class Die(val sides: Int, val label: String, val color: Color)

data class DieRoll(val value: Int, val sides: Int, val label: String, val color: Color)

data class RollResult(
    val rolls: List<DieRoll>,
    val total: Int,
    val rawSum: Int,
    val modifier: Int,
    val expression: String,
    val isCrit: Boolean,
    val isCritFail: Boolean,
)

data class HistoryEntry(val expression: String, val total: Int, val time: String)

enum class AdvantageMode { ADV, DIS }

// ── Constants ────────────────────────────────────────────

val DICE = listOf(
    Die(4,   "d4",  Color(0xFFE74C3C)),
    Die(6,   "d6",  Color(0xFFE67E22)),
    Die(8,   "d8",  Color(0xFFF1C40F)),
    Die(10,  "d10", Color(0xFF2ECC71)),
    Die(12,  "d12", Color(0xFF3498DB)),
    Die(20,  "d20", Color(0xFF9B59B6)),
    Die(100, "d%",  Color(0xFFE91E63)),
)

val BgColor      = Color(0xFF0E0E14)
val SurfaceColor = Color(0xFF1A1A26)
val RaisedColor  = Color(0xFF22223A)
val BorderColor  = Color(0xFF2E2E4A)
val DimBorder    = Color(0xFF3E3E5A)
val AccentColor  = Color(0xFFC8A84B)
val MutedColor   = Color(0xFF8A8AAA)
val RedColor     = Color(0xFFE74C3C)
val GreenColor   = Color(0xFF2ECC71)

// ── Activity ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DnDiceTheme { DiceRollerScreen() }
        }
    }
}

// ── Theme ────────────────────────────────────────────────

@Composable
fun DnDiceTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BgColor,
            surface = SurfaceColor,
            primary = AccentColor,
            onPrimary = Color(0xFF111111),
            onBackground = Color(0xFFE8E4D9),
            onSurface = Color(0xFFE8E4D9),
        ),
        content = content,
    )
}

// ── Main screen ──────────────────────────────────────────

@Composable
fun DiceRollerScreen() {
    var counts       by remember { mutableStateOf(DICE.associate { it.sides to 0 }) }
    var modifierText by remember { mutableStateOf("0") }
    var advMode      by remember { mutableStateOf<AdvantageMode?>(null) }
    var rollResult   by remember { mutableStateOf<RollResult?>(null) }
    var history      by remember { mutableStateOf(listOf<HistoryEntry>()) }
    var isRolling    by remember { mutableStateOf(false) }

    val rollBtnScale = remember { Animatable(1f) }
    val totalScale   = remember { Animatable(1f) }
    val resultAlpha  = remember { Animatable(1f) }

    val scope  = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val hasDice = counts.values.any { it > 0 }

    fun addDie(sides: Int) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        counts = counts + (sides to (counts[sides]!! + 1))
    }

    fun removeDie(sides: Int) {
        if ((counts[sides] ?: 0) == 0) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        counts = counts + (sides to (counts[sides]!! - 1))
    }

    fun clearAll() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        counts = DICE.associate { it.sides to 0 }
        advMode = null
        modifierText = "0"
        rollResult = null
    }

    fun roll() {
        if (isRolling || !hasDice) return
        scope.launch {
            isRolling = true
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)

            launch {
                rollBtnScale.animateTo(0.93f, tween(80))
                rollBtnScale.animateTo(1f, tween(140))
            }

            resultAlpha.animateTo(0f, tween(120))

            val mod   = modifierText.toIntOrNull() ?: 0
            val rolls = mutableListOf<DieRoll>()
            var expr  = ""

            DICE.forEach { die ->
                val count = counts[die.sides] ?: 0
                if (count == 0) return@forEach

                if ((advMode == AdvantageMode.ADV || advMode == AdvantageMode.DIS)
                    && die.sides == 20 && count == 1
                ) {
                    val a = (1..20).random()
                    val b = (1..20).random()
                    val kept = if (advMode == AdvantageMode.ADV) maxOf(a, b) else minOf(a, b)
                    rolls.add(DieRoll(kept, 20, die.label, die.color))
                } else {
                    repeat(count) {
                        rolls.add(DieRoll((1..die.sides).random(), die.sides, die.label, die.color))
                    }
                }

                expr += (if (expr.isEmpty()) "" else " + ") +
                        (if (count > 1) count.toString() else "") + die.label
            }

            val rawSum     = rolls.sumOf { it.value }
            val total      = rawSum + mod
            val isCrit     = rolls.any { it.sides == 20 && it.value == 20 }
            val isCritFail = !isCrit && rolls.any { it.sides == 20 && it.value == 1 }

            if (mod != 0) expr += (if (mod > 0) " + " else " − ") + abs(mod)
            if (advMode == AdvantageMode.ADV) expr += " (Adv)"
            if (advMode == AdvantageMode.DIS) expr += " (Dis)"

            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
            rollResult = RollResult(rolls, total, rawSum, mod, expr, isCrit, isCritFail)
            history    = (listOf(HistoryEntry(expr, total, time)) + history).take(5)

            resultAlpha.animateTo(1f, tween(180))
            launch {
                totalScale.animateTo(1.2f, tween(140))
                totalScale.animateTo(1f, tween(200))
            }

            if (isCrit || isCritFail) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            isRolling = false
        }
    }

    Scaffold(
        containerColor = BgColor,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            RollButtonBar(
                enabled = hasDice && !isRolling,
                scale = rollBtnScale.value,
                onRoll = ::roll,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppHeader()

            DiceGrid(counts = counts, onAdd = ::addDie, onRemove = ::removeDie)

            Text(
                text = "Tap to add  ·  Hold to remove",
                color = DimBorder,
                fontSize = 11.sp,
                letterSpacing = 1.sp,
            )

            ControlsRow(
                modifierText = modifierText,
                onModifierChange = { modifierText = it },
                advMode = advMode,
                onAdvToggle = { mode ->
                    advMode = if (advMode == mode) null else mode
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                onClear = ::clearAll,
            )

            ResultPanel(
                result = rollResult,
                alpha = resultAlpha.value,
                totalScale = totalScale.value,
            )

            if (history.isNotEmpty()) {
                RollHistory(history)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Header ───────────────────────────────────────────────

@Composable
fun AppHeader() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "DnDice",
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = AccentColor,
            letterSpacing = 5.sp,
        )
        Text(
            text = "ROLL THE BONES",
            fontSize = 11.sp,
            color = MutedColor,
            letterSpacing = 7.sp,
        )
    }
}

// ── Dice grid ────────────────────────────────────────────

@Composable
fun DiceGrid(
    counts: Map<Int, Int>,
    onAdd: (Int) -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        listOf(DICE.take(4), DICE.drop(4)).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { die ->
                    DieButton(
                        die = die,
                        count = counts[die.sides] ?: 0,
                        onAdd = { onAdd(die.sides) },
                        onRemove = { onRemove(die.sides) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad last row to maintain grid alignment
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ── Die button ───────────────────────────────────────────

@Composable
fun DieButton(
    die: Die,
    count: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = count > 0

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) die.color.copy(alpha = 0.10f) else RaisedColor)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) die.color else BorderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onAdd() },
                    onLongPress = { onRemove() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DieShape(sides = die.sides, color = if (active) die.color else DimBorder)
            Text(
                text = die.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) die.color else MutedColor,
                letterSpacing = 1.sp,
            )
        }

        if (active) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(die.color),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = count.toString(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF111111),
                )
            }
        }
    }
}

// ── Die shape (Canvas) ───────────────────────────────────

@Composable
fun DieShape(sides: Int, color: Color) {
    Canvas(modifier = Modifier.size(30.dp)) {
        val w      = size.width
        val h      = size.height
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun polygon(n: Int, startAngleDeg: Float = -90f): Path {
            val cx = w / 2f
            val cy = h / 2f
            val r  = minOf(w, h) / 2f - stroke.width / 2f
            return Path().apply {
                for (i in 0 until n) {
                    val a = Math.toRadians((startAngleDeg + i * (360f / n)).toDouble())
                    val x = cx + r * cos(a).toFloat()
                    val y = cy + r * sin(a).toFloat()
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
        }

        when (sides) {
            4 -> drawPath(
                Path().apply {
                    val pad = stroke.width / 2f
                    moveTo(w / 2f, pad)
                    lineTo(w - pad, h - pad)
                    lineTo(pad, h - pad)
                    close()
                },
                color, style = stroke,
            )
            6 -> drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2),
                size = Size(w - stroke.width, h - stroke.width),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = stroke,
            )
            8  -> drawPath(polygon(4, startAngleDeg = -45f), color, style = stroke)
            10 -> drawPath(polygon(5), color, style = stroke)
            12 -> drawPath(polygon(6), color, style = stroke)
            20 -> drawPath(polygon(6, startAngleDeg = 0f), color, style = stroke)
            100 -> drawCircle(
                color = color,
                radius = minOf(w, h) / 2f - stroke.width / 2f,
                style = stroke,
            )
        }
    }
}

// ── Controls row ─────────────────────────────────────────

@Composable
fun ControlsRow(
    modifierText: String,
    onModifierChange: (String) -> Unit,
    advMode: AdvantageMode?,
    onAdvToggle: (AdvantageMode) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Modifier
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(RaisedColor)
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .height(44.dp)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("MOD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MutedColor, letterSpacing = 2.sp)
            BasicModifierInput(value = modifierText, onValueChange = onModifierChange)
        }

        AdvButton(label = "ADV", active = advMode == AdvantageMode.ADV, activeColor = GreenColor) {
            onAdvToggle(AdvantageMode.ADV)
        }
        AdvButton(label = "DIS", active = advMode == AdvantageMode.DIS, activeColor = RedColor) {
            onAdvToggle(AdvantageMode.DIS)
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(RaisedColor)
                .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                .clickable { onClear() }
                .height(44.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Clear", fontSize = 13.sp, color = MutedColor)
        }
    }
}

@Composable
fun BasicModifierInput(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = { v ->
            if (v.matches(Regex("^-?\\d{0,3}$")) || v == "-") onValueChange(v)
        },
        modifier = Modifier.width(60.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            color = Color(0xFFE8E4D9),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = AccentColor,
            focusedTextColor = Color(0xFFE8E4D9),
            unfocusedTextColor = Color(0xFFE8E4D9),
        ),
    )
}

@Composable
fun AdvButton(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeColor.copy(alpha = 0.12f) else RaisedColor)
            .border(1.dp, if (active) activeColor else BorderColor, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .height(44.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) activeColor else MutedColor,
            letterSpacing = 1.sp,
        )
    }
}

// ── Result panel ─────────────────────────────────────────

@Composable
fun ResultPanel(result: RollResult?, alpha: Float, totalScale: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceColor)
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp))
            .padding(20.dp)
            .graphicsLayer(alpha = alpha),
        contentAlignment = Alignment.Center,
    ) {
        if (result == null) {
            Text(
                text = "Select dice and roll",
                color = MutedColor,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(result.expression, color = MutedColor, fontSize = 12.sp, letterSpacing = 1.sp)

                // Chips — split into rows of 5
                result.rolls.chunked(5).forEach { rowRolls ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        rowRolls.forEach { roll ->
                            DieChip(roll)
                        }
                    }
                }

                if (result.modifier != 0) {
                    Text(
                        text = "${result.rawSum} ${if (result.modifier > 0) "+" else "−"} ${abs(result.modifier)} mod",
                        color = MutedColor,
                        fontSize = 12.sp,
                    )
                }

                Text(
                    text = result.total.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = if (result.isCrit) Color(0xFFF0D080) else AccentColor,
                    lineHeight = 72.sp,
                    modifier = Modifier.scale(totalScale),
                )

                if (result.isCrit) {
                    CritBadge("NATURAL 20  ✦", AccentColor)
                }
                if (result.isCritFail) {
                    CritBadge("CRITICAL FAIL", RedColor)
                }
            }
        }
    }
}

@Composable
fun DieChip(roll: DieRoll) {
    val isMax = roll.value == roll.sides
    val isMin = roll.value == 1
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(RaisedColor)
            .border(
                width = 1.5.dp,
                color = when {
                    isMax -> roll.color
                    isMin -> Color(0xFF444444)
                    else  -> BorderColor
                },
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = roll.value.toString(),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isMax) roll.color else if (isMin) MutedColor.copy(alpha = 0.5f) else Color(0xFFE8E4D9),
        )
        Text(text = roll.label, fontSize = 9.sp, color = MutedColor, letterSpacing = 1.sp)
    }
}

@Composable
fun CritBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = color, letterSpacing = 3.sp)
    }
}

// ── Roll history ─────────────────────────────────────────

@Composable
fun RollHistory(history: List<HistoryEntry>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "RECENT ROLLS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MutedColor,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
        )
        history.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceColor)
                    .border(1.dp, BorderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = entry.expression,
                    modifier = Modifier.weight(1f),
                    color = MutedColor,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(entry.total.toString(), fontSize = 19.sp, fontWeight = FontWeight.Black, color = AccentColor)
                Text(entry.time, fontSize = 11.sp, color = BorderColor)
            }
        }
    }
}

// ── Roll button ──────────────────────────────────────────

@Composable
fun RollButtonBar(enabled: Boolean, scale: Float, onRoll: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BgColor.copy(alpha = 0.96f),
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp, top = 10.dp)
                .fillMaxWidth()
                .scale(scale)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    brush = if (enabled)
                        Brush.linearGradient(listOf(Color(0xFFA07820), Color(0xFFC8A84B), Color(0xFFD4B860)))
                    else
                        Brush.linearGradient(listOf(Color(0xFF252535), Color(0xFF252535))),
                )
                .clickable(enabled = enabled, onClick = onRoll)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ROLL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (enabled) Color(0xFF111111) else Color(0xFF555566),
                letterSpacing = 6.sp,
            )
        }
    }
}
