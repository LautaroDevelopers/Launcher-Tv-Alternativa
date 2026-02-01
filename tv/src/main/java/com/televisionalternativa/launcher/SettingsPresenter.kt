package com.televisionalternativa.launcher

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.Presenter

/**
 * Presenter para items de configuración
 */
class SettingsPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val view = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(ITEM_WIDTH, ITEM_HEIGHT)
            isFocusable = true
            isFocusableInTouchMode = true
            gravity = Gravity.CENTER
            textSize = 16f
            setTextColor(Color.WHITE)
            setBackgroundColor(ContextCompat.getColor(context, R.color.card_background))
            setPadding(16, 16, 16, 16)
        }
        return ViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val textView = viewHolder.view as TextView
        val settingItem = item as? com.televisionalternativa.launcher.domain.LauncherItem
        textView.text = settingItem?.title ?: item.toString()
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        // No cleanup needed
    }

    companion object {
        private const val ITEM_WIDTH = 240
        private const val ITEM_HEIGHT = 80
    }
}
