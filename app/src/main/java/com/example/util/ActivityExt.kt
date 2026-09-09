package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Cari Activity dari Context, karena LocalContext.current bisa jadi ContextWrapper (Compose). */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
