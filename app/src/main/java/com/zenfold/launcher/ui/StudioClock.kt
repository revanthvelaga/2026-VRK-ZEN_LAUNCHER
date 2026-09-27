package com.zenfold.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenfold.launcher.style.ClockDesign
import com.zenfold.launcher.style.CustomStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudioClock(style: CustomStyle, now: Long, compact: Boolean = false) {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val locale = Locale.getDefault()
    val date = remember(now, locale) { SimpleDateFormat("EEEE, d MMMM", locale).format(Date(now)) }
    val time = remember(now, is24Hour, locale) { SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm", locale).format(Date(now)) }
    val period = remember(now, locale) { SimpleDateFormat("a", locale).format(Date(now)) }
    val editorial = style.clockDesign != ClockDesign.CLASSIC
    val textSize = (if (compact) 48 else if (editorial) 80 else 72) * style.fontScale.scale
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (style.clockCentered) Alignment.CenterHorizontally else Alignment.Start) {
        if (editorial) Text(date.uppercase(locale), fontSize = (if (compact) 10 else 12).sp,
            letterSpacing = 1.4.sp, color = style.onSurfaceVariant, fontWeight = FontWeight.Medium)
        Text(if (style.clockDesign == ClockDesign.STACKED) time.replace(":", "\n") else time,
            fontSize = textSize.sp, lineHeight = (textSize * 0.94f).sp,
            letterSpacing = (-3).sp, fontWeight = style.clockWeight, color = style.onBackground)
        if (!is24Hour) Text(period, fontSize = 12.sp, color = style.onSurfaceVariant)
        if (!editorial) Text(date, fontSize = (14 * style.fontScale.scale).sp, color = style.onSurfaceVariant)
        Spacer(Modifier.height(if (compact) 4.dp else 10.dp))
    }
}
