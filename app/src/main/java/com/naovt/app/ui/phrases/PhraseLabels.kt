package com.naovt.app.ui.phrases

import androidx.annotation.StringRes
import com.naovt.app.R
import com.naovt.app.data.PhraseCategory

@StringRes
fun PhraseCategory.labelRes(): Int = when (this) {
    PhraseCategory.Normal -> R.string.category_normal
    PhraseCategory.Consuelo -> R.string.category_comfort
    PhraseCategory.Meme -> R.string.category_meme
}
