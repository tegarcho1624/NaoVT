package com.naovt.app.ui.alerts

import androidx.annotation.StringRes
import com.naovt.app.R
import com.naovt.app.data.AlertRepeat

/** Opciones de repetición, en el orden del menú. */
val SelectableRepeats = listOf(
    AlertRepeat.None,
    AlertRepeat.Daily,
    AlertRepeat.Weekdays,
    AlertRepeat.Weekly,
)

@StringRes
fun AlertRepeat.labelRes(): Int = when (this) {
    AlertRepeat.None -> R.string.repeat_none
    AlertRepeat.Daily -> R.string.repeat_daily
    AlertRepeat.Weekly -> R.string.repeat_weekly
    AlertRepeat.Weekdays -> R.string.repeat_weekdays
}
