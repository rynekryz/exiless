package com.rynekryz.exiless.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import kotlin.math.max

class StatBars @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    class Entry(val label: String, val value: Int)

    private var entries: List<Entry> = emptyList()

    private val accent = colorOf(android.R.attr.colorAccent, 0xFF0671EB.toInt())
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        alpha = 38
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOf(android.R.attr.textColorPrimary, Color.WHITE)
        textSize = 15f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorOf(android.R.attr.textColorSecondary, Color.GRAY)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    fun setEntries(list: List<Entry>) {
        entries = list
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (entries.isEmpty()) return
        val d = resources.displayMetrics.density
        val pad = 6f * d
        val valueH = valuePaint.textSize
        val labelH = labelPaint.textSize * 1.6f
        val barW = 34f * d
        val barH = max(4f * d, height - valueH - labelH - pad * 2)
        val max = max(1, entries.maxOf { it.value })
        val step = width.toFloat() / entries.size
        val radius = barW / 2f
        for ((i, e) in entries.withIndex()) {
            val cx = (i + 0.5f) * step
            val h = 4f * d + barH * (e.value.coerceIn(0, max).toFloat() / max)
            val rect = RectF(cx - barW / 2, pad + barH - h, cx + barW / 2, pad + barH)
            canvas.drawRoundRect(rect, radius, radius, if (e.value <= 0) emptyPaint else barPaint)
            canvas.drawText(e.value.toString(), cx, rect.top - valueH * 0.45f, valuePaint)
            canvas.drawText(e.label, cx, height - labelH * 0.30f, labelPaint)
        }
    }

    private fun colorOf(attr: Int, fallback: Int): Int {
        val tv = TypedValue()
        if (!context.theme.resolveAttribute(attr, tv, true)) return fallback
        return when (tv.type) {
            TypedValue.TYPE_REFERENCE -> runCatching { resources.getColor(tv.resourceId, context.theme) }.getOrDefault(fallback)
            else -> tv.data
        }
    }
}
