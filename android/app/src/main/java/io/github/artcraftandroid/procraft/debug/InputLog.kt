package io.github.artcraftandroid.procraft.debug

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A live log of input, for finding out what a device sends: every touch, pen and key event the
 * window gets ("win") and the canvas gets ("canvas"), and what the app made of it. Shown over the
 * app, and written to logcat (tag ProcraftInput), only in the "ProCraft Fixed" build.
 */
object InputLog {
    var enabled = false
    val lines = mutableStateListOf<String>()
    private const val MAX = 16
    private val lastMove = HashMap<String, String>()

    fun add(line: String) {
        if (!enabled) return
        Log.d("ProcraftInput", line)
        val t = android.os.SystemClock.uptimeMillis() % 100_000
        lines.add("%05d %s".format(t, line))
        while (lines.size > MAX) lines.removeAt(0)
    }

    fun motion(where: String, e: MotionEvent) {
        if (!enabled) return
        val m = e.actionMasked
        val pointers = (0 until e.pointerCount).joinToString(",") { "${e.getPointerId(it)}:${tool(e.getToolType(it))}" }
        val buttons = if (e.buttonState != 0) " buttons=${buttonNames(e.buttonState)}" else ""
        // moves only when the pointers down or the buttons held change
        if (m == MotionEvent.ACTION_MOVE || m == MotionEvent.ACTION_HOVER_MOVE) {
            val key = "$m $pointers$buttons"
            if (lastMove[where] == key) return
            lastMove[where] = key
        }
        val which = when (m) {
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP -> " #${e.getPointerId(e.actionIndex)}"
            else -> ""
        }
        val pressed = when (m) {
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> " button=${buttonNames(e.actionButton)}"
            else -> ""
        }
        val cancelled = if (e.flags and MotionEvent.FLAG_CANCELED != 0) " CANCELED" else ""
        val name = MotionEvent.actionToString(m).removePrefix("ACTION_").substringBefore('(')
        add("$where $name$which [$pointers]$buttons$pressed$cancelled")
    }

    fun key(e: KeyEvent) {
        if (!enabled) return
        val dir = if (e.action == KeyEvent.ACTION_DOWN) "down" else "up"
        if (e.repeatCount > 0) return
        add("win key ${KeyEvent.keyCodeToString(e.keyCode).removePrefix("KEYCODE_")} $dir (${e.device?.name ?: "?"})")
    }

    /** What the shared touches asked for, after a touch event (cursor moves left out). */
    fun commands(out: List<Any>) {
        if (!enabled) return
        val names = out.map(::commandName).filter { !it.startsWith("Cursor") }
        if (names.isNotEmpty()) add("  -> ${names.joinToString(", ")}")
    }

    private fun commandName(c: Any): String =
        c.toString().substringBefore('(').substringBefore('@').substringAfterLast('$').substringAfterLast('.')

    private fun tool(t: Int) = when (t) {
        MotionEvent.TOOL_TYPE_FINGER -> "finger"
        MotionEvent.TOOL_TYPE_STYLUS -> "pen"
        MotionEvent.TOOL_TYPE_ERASER -> "pen-eraser"
        MotionEvent.TOOL_TYPE_MOUSE -> "mouse"
        5 -> "palm"
        else -> "tool$t"
    }

    private fun buttonNames(b: Int): String {
        val names = listOf(
            MotionEvent.BUTTON_STYLUS_PRIMARY to "pen1",
            MotionEvent.BUTTON_STYLUS_SECONDARY to "pen2",
            MotionEvent.BUTTON_PRIMARY to "primary",
            MotionEvent.BUTTON_SECONDARY to "secondary",
            MotionEvent.BUTTON_TERTIARY to "tertiary",
        ).filter { b and it.first != 0 }.map { it.second }
        return names.joinToString("+").ifEmpty { "0x" + Integer.toHexString(b) }
    }
}

/** The log over the app, at the bottom; it takes no touches. */
@Composable
fun InputLogOverlay() {
    if (!InputLog.enabled) return
    Box(Modifier.fillMaxSize().padding(bottom = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.widthIn(max = 620.dp).background(Color(0xCC000000), RoundedCornerShape(8.dp)).padding(8.dp)) {
            val style = TextStyle(color = Color(0xFF8CF5A8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            if (InputLog.lines.isEmpty()) BasicText("input log: touch the screen", style = style)
            for (line in InputLog.lines) BasicText(line, style = style, maxLines = 1, softWrap = false)
        }
    }
}
