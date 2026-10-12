package org.ivanivorontsov.mudrost.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.ivanivorontsov.mudrost.engine.Entry
import org.ivanivorontsov.mudrost.engine.Quote
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ru = Locale("ru")

fun quoteText(q: Quote): String = buildString {
    append("«").append(q.text).append("»\n— ").append(q.author)
    if (q.source.isNotBlank()) append(", ").append(q.source)
}

fun entryText(e: Entry, q: Quote?): String = buildString {
    append(e.title).append("\n\n").append(e.answer)
    if (q != null) append("\n\n").append(quoteText(q))
    append("\n\n— из приложения «Мудрость»")
}

fun share(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    ctx.startActivity(Intent.createChooser(send, "Поделиться"))
}

fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Мудрость", text))
    Toast.makeText(ctx, "Скопировано", Toast.LENGTH_SHORT).show()
}

fun formatTime(t: Long, now: Long = System.currentTimeMillis()): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val dayDiff = if (sameYear) n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR) else Int.MAX_VALUE
    val time = SimpleDateFormat("HH:mm", ru).format(Date(t))
    return when (dayDiff) {
        0 -> "Сегодня, $time"
        1 -> "Вчера, $time"
        else -> SimpleDateFormat(if (sameYear) "d MMMM, HH:mm" else "d MMMM yyyy, HH:mm", ru).format(Date(t))
    }
}

/** 1 вопрос, 2 вопроса, 5 вопросов */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10; val m100 = n % 100
    val w = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}
