package com.zariapps.dndice

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.compose.animation.animateColorAsState
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

// ── Dice config ──────────────────────────────────────────

val DICE = listOf(
    Die(4,   "d4",  Color(0xFFE74C3C)),
    Die(6,   "d6",  Color(0xFFE67E22)),
    Die(8,   "d8",  Color(0xFFF1C40F)),
    Die(10,  "d10", Color(0xFF2ECC71)),
    Die(12,  "d12", Color(0xFF3498DB)),
    Die(20,  "d20", Color(0xFF9B59B6)),
    Die(100, "d%",  Color(0xFFE91E63)),
)

// ── Theme colors ─────────────────────────────────────────

data class AppColors(
    val bg: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val dimBorder: Color,
    val accent: Color,
    val muted: Color,
    val text: Color,
    val red: Color,
    val green: Color,
    val isDark: Boolean,
)

val darkColors = AppColors(
    bg        = Color(0xFF0E0E14),
    surface   = Color(0xFF1A1A26),
    raised    = Color(0xFF22223A),
    border    = Color(0xFF2E2E4A),
    dimBorder = Color(0xFF3E3E5A),
    accent    = Color(0xFFC8A84B),
    muted     = Color(0xFF8A8AAA),
    text      = Color(0xFFE8E4D9),
    red       = Color(0xFFE74C3C),
    green     = Color(0xFF2ECC71),
    isDark    = true,
)

val lightColors = AppColors(
    bg        = Color(0xFFF5F0E8),
    surface   = Color(0xFFEBE5D6),
    raised    = Color(0xFFE2DBCA),
    border    = Color(0xFFC5B89A),
    dimBorder = Color(0xFFA89E88),
    accent    = Color(0xFF7A5C10),
    muted     = Color(0xFF6B6050),
    text      = Color(0xFF2A1F0A),
    red       = Color(0xFFCC3322),
    green     = Color(0xFF1A7A3A),
    isDark    = false,
)

val LocalColors = compositionLocalOf { darkColors }

// ── Activity ─────────────────────────────────────────────

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this)
        enableEdgeToEdge()
        val prefs = getSharedPreferences("dndice_prefs", MODE_PRIVATE)
        setContent {
            var isDark by remember { mutableStateOf(prefs.getBoolean("is_dark", false)) }
            DnDiceTheme(isDark = isDark) {
                DiceRollerScreen(
                    isDark = isDark,
                    onToggleTheme = {
                        isDark = !isDark
                        prefs.edit().putBoolean("is_dark", isDark).apply()
                    },
                )
            }
        }
    }
}

// ── Theme ────────────────────────────────────────────────

@Composable
fun DnDiceTheme(isDark: Boolean, content: @Composable () -> Unit) {
    val c = if (isDark) darkColors else lightColors
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(
            colorScheme = if (isDark) {
                darkColorScheme(
                    background = c.bg,
                    surface    = c.surface,
                    primary    = c.accent,
                    onPrimary  = Color(0xFF111111),
                    onBackground = c.text,
                    onSurface  = c.text,
                )
            } else {
                lightColorScheme(
                    background = c.bg,
                    surface    = c.surface,
                    primary    = c.accent,
                    onPrimary  = Color(0xFFFFFFFF),
                    onBackground = c.text,
                    onSurface  = c.text,
                )
            },
            content = content,
        )
    }
}

// ── Main screen ──────────────────────────────────────────

@Composable
fun DiceRollerScreen(isDark: Boolean, onToggleTheme: () -> Unit) {
    val c = LocalColors.current

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

    // Animate bg color on theme switch
    val animBg by animateColorAsState(c.bg, tween(300), label = "bg")

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
        containerColor = animBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Column {
                BannerAd()
                RollButtonBar(
                    enabled = hasDice && !isRolling,
                    scale = rollBtnScale.value,
                    onRoll = ::roll,
                )
            }
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
            AppHeader(isDark = isDark, onToggleTheme = onToggleTheme)

            DiceGrid(counts = counts, onAdd = ::addDie, onRemove = ::removeDie)

            Text(
                text = "Tap to add  ·  Hold to remove",
                color = c.dimBorder,
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
fun AppHeader(isDark: Boolean, onToggleTheme: () -> Unit) {
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Spacer to balance the toggle button on the right
        Spacer(Modifier.size(44.dp))

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "DnDice",
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                color = c.accent,
                letterSpacing = 5.sp,
            )
            Text(
                text = "ROLL THE BONES",
                fontSize = 11.sp,
                color = c.muted,
                letterSpacing = 7.sp,
            )
        }

        Spacer(Modifier.weight(1f))

        // Theme toggle button
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.raised)
                .border(1.dp, c.border, CircleShape)
                .clickable { onToggleTheme() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isDark) "☀" else "☾",
                fontSize = 18.sp,
                color = c.accent,
            )
        }
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
                repeat(4 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
    val c = LocalColors.current
    val active = count > 0

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) die.color.copy(alpha = 0.10f) else c.raised)
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) die.color else c.border,
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
            DieShape(sides = die.sides, color = if (active) die.color else c.dimBorder)
            Text(
                text = die.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (active) die.color else c.muted,
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
    val c = LocalColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.raised)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("MOD", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.muted, letterSpacing = 2.sp)
            BasicModifierInput(value = modifierText, onValueChange = onModifierChange)
        }

        AdvButton(label = "ADV", active = advMode == AdvantageMode.ADV, activeColor = c.green) {
            onAdvToggle(AdvantageMode.ADV)
        }
        AdvButton(label = "DIS", active = advMode == AdvantageMode.DIS, activeColor = c.red) {
            onAdvToggle(AdvantageMode.DIS)
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(c.raised)
                .border(1.dp, c.border, RoundedCornerShape(10.dp))
                .clickable { onClear() }
                .height(52.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Clear", fontSize = 13.sp, color = c.muted)
        }
    }
}

@Composable
fun BasicModifierInput(value: String, onValueChange: (String) -> Unit) {
    val c = LocalColors.current
    TextField(
        value = value,
        onValueChange = { v ->
            if (v.matches(Regex("^-?\\d{0,3}$")) || v == "-") onValueChange(v)
        },
        modifier = Modifier.width(72.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = TextStyle(
            color = c.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = c.accent,
            focusedTextColor = c.text,
            unfocusedTextColor = c.text,
        ),
    )
}

@Composable
fun AdvButton(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) activeColor.copy(alpha = 0.12f) else c.raised)
            .border(1.dp, if (active) activeColor else c.border, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .height(52.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (active) activeColor else c.muted,
            letterSpacing = 1.sp,
        )
    }
}

// ── Result panel ─────────────────────────────────────────

@Composable
fun ResultPanel(result: RollResult?, alpha: Float, totalScale: Float) {
    val c = LocalColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(16.dp))
            .padding(20.dp)
            .graphicsLayer(alpha = alpha),
        contentAlignment = Alignment.Center,
    ) {
        if (result == null) {
            Text(
                text = "Select dice and roll",
                color = c.muted,
                fontSize = 13.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(vertical = 20.dp),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(result.expression, color = c.muted, fontSize = 12.sp, letterSpacing = 1.sp)

                result.rolls.chunked(5).forEach { rowRolls ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.wrapContentWidth(),
                    ) {
                        rowRolls.forEach { roll -> DieChip(roll) }
                    }
                }

                if (result.modifier != 0) {
                    Text(
                        text = "${result.rawSum} ${if (result.modifier > 0) "+" else "−"} ${abs(result.modifier)} mod",
                        color = c.muted,
                        fontSize = 12.sp,
                    )
                }

                Text(
                    text = result.total.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    color = if (result.isCrit) Color(0xFFF0D080) else c.accent,
                    lineHeight = 72.sp,
                    modifier = Modifier.scale(totalScale),
                )

                if (result.isCrit)     CritBadge("NATURAL 20  ✦", c.accent)
                if (result.isCritFail) CritBadge("CRITICAL FAIL", c.red)
            }
        }
    }
}

@Composable
fun DieChip(roll: DieRoll) {
    val c = LocalColors.current
    val isMax = roll.value == roll.sides
    val isMin = roll.value == 1
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(c.raised)
            .border(
                width = 1.5.dp,
                color = when {
                    isMax -> roll.color
                    isMin -> c.border
                    else  -> c.border
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
            color = if (isMax) roll.color else if (isMin) c.muted.copy(alpha = 0.5f) else c.text,
        )
        Text(text = roll.label, fontSize = 9.sp, color = c.muted, letterSpacing = 1.sp)
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
    val c = LocalColors.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "RECENT ROLLS",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = c.muted,
            letterSpacing = 4.sp,
            modifier = Modifier.padding(start = 2.dp, bottom = 2.dp),
        )
        history.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(c.surface)
                    .border(1.dp, c.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = entry.expression,
                    modifier = Modifier.weight(1f),
                    color = c.muted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(entry.total.toString(), fontSize = 19.sp, fontWeight = FontWeight.Black, color = c.accent)
                Text(entry.time, fontSize = 11.sp, color = c.border)
            }
        }
    }
}

// ── Banner ad ────────────────────────────────────────────

@Composable
fun BannerAd() {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3572341533498507/1079824166"
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                )
                loadAd(AdRequest.Builder().build())
            }
        },
    )
}

// ── Roll button ──────────────────────────────────────────

@Composable
fun RollButtonBar(enabled: Boolean, scale: Float, onRoll: () -> Unit) {
    val c = LocalColors.current
    val animSurface by animateColorAsState(c.bg, tween(300), label = "btnBg")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = animSurface.copy(alpha = 0.96f),
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
                        Brush.linearGradient(listOf(c.raised, c.raised)),
                )
                .clickable(enabled = enabled, onClick = onRoll)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ROLL",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = if (enabled) Color(0xFF111111) else c.muted,
                letterSpacing = 6.sp,
            )
        }
    }
}
