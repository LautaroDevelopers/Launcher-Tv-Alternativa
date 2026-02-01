package com.televisionalternativa.launcher

import android.view.ViewGroup
import androidx.leanback.widget.ListRowPresenter
import androidx.leanback.widget.RowPresenter

/**
 * ListRowPresenter custom que desactiva el clipping
 * para que el glow/sombra de las cards se vea correctamente.
 */
class NoClipListRowPresenter : ListRowPresenter() {
    
    init {
        shadowEnabled = false
    }
    
    override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder {
        val viewHolder = super.createRowViewHolder(parent)
        
        // Desactivar clipping en toda la jerarquía
        val view = viewHolder.view
        if (view is ViewGroup) {
            disableClipping(view)
        }
        
        return viewHolder
    }
    
    override fun onBindRowViewHolder(holder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(holder, item)
        
        // Asegurarnos de que el clipping esté desactivado después de bind
        val view = holder.view
        if (view is ViewGroup) {
            disableClipping(view)
        }
    }
    
    private fun disableClipping(viewGroup: ViewGroup) {
        viewGroup.clipChildren = false
        viewGroup.clipToPadding = false
        
        // Recursivamente desactivar en hijos
        for (i in 0 until viewGroup.childCount) {
            val child = viewGroup.getChildAt(i)
            if (child is ViewGroup) {
                disableClipping(child)
            }
        }
    }
}
