package com.televisionalternativa.launcher

import android.graphics.drawable.Drawable
import android.os.Parcelable
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppInfo(
    val packageName: String,
    val label: String
) : Parcelable {
    @IgnoredOnParcel
    @Transient
    var icon: Drawable? = null
}

