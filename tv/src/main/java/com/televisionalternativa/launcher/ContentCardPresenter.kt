package com.televisionalternativa.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.Presenter
import com.televisionalternativa.launcher.domain.LauncherItem

/**
 * Presenter para contenido (películas, canales, etc.)
 * Muestra tarjetas con imagen y título
 */
class ContentCardPresenter : Presenter() {

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val view = inflater.inflate(R.layout.content_card_view, parent, false)
        
        // Agregar animación de escala y elevación en focus
        view.onFocusChangeListener = View.OnFocusChangeListener { v, hasFocus ->
            val scale = if (hasFocus) 1.08f else 1.0f
            v.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(150)
                .start()
            
            // Elevar cuando tiene focus
            v.elevation = if (hasFocus) 16f else 4f
        }
        
        return ContentViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any?) {
        val contentViewHolder = viewHolder as ContentViewHolder
        val contentItem = item as? LauncherItem ?: return

        contentViewHolder.titleText.text = contentItem.title
        
        // TODO: Aquí iría la carga de imagen real usando Glide
        if (contentItem.imageUrl.isNullOrEmpty()) {
            contentViewHolder.imageView.setImageDrawable(null)
        } else {
            contentViewHolder.imageView.setImageDrawable(null)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val contentViewHolder = viewHolder as ContentViewHolder
        contentViewHolder.imageView.setImageDrawable(null)
    }

    inner class ContentViewHolder(view: View) : ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.content_image)
        val titleText: TextView = view.findViewById(R.id.content_title)
    }
}
