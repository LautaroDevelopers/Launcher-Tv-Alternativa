package com.televisionalternativa.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter

class AppCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val context = parent.context
        
        // Contenedor con el glow azul
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            clipChildren = false
            clipToPadding = false
            // Padding para que el glow se vea
            setPadding(12, 12, 12, 12)
        }
        
        val cardView = ImageCardView(context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(280, 158) // Formato 16:9

            mainImageView?.scaleType = ImageView.ScaleType.CENTER_CROP
            infoVisibility = ImageCardView.GONE
            
            // Background normal
            val normalBg = GradientDrawable().apply {
                setColor(ContextCompat.getColor(context, R.color.card_background))
                cornerRadius = 16f
                setStroke(2, ContextCompat.getColor(context, R.color.divider))
            }
            background = normalBg
            
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        
        // Glow drawable (se aplica al container)
        val glowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            // Azul con transparencia para el glow
            setColor(Color.parseColor("#402196F3"))
        }
        
        val noGlowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f
            setColor(Color.TRANSPARENT)
        }
        
        cardView.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                // Borde azul brillante
                val focusBg = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(context, R.color.card_background))
                    cornerRadius = 16f
                    setStroke(4, Color.parseColor("#64B5F6")) // Azul claro
                }
                v.background = focusBg
                container.background = glowDrawable
            } else {
                val normalBg = GradientDrawable().apply {
                    setColor(ContextCompat.getColor(context, R.color.card_background))
                    cornerRadius = 16f
                    setStroke(2, ContextCompat.getColor(context, R.color.divider))
                }
                v.background = normalBg
                container.background = noGlowDrawable
            }
            
            // Escala
            val scale = if (hasFocus) 1.1f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .start()
        }
        
        container.addView(cardView)
        return ViewHolder(container)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val appInfo = item as? AppInfo ?: return
        val container = viewHolder.view as FrameLayout
        val cardView = container.getChildAt(0) as ImageCardView

        cardView.mainImage = appInfo.icon
        cardView.titleText = null
        cardView.contentText = null
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val container = viewHolder.view as FrameLayout
        val cardView = container.getChildAt(0) as ImageCardView
        cardView.mainImage = null
    }
}
