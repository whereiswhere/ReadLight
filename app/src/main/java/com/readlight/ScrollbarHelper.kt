package com.readlight

import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.roundToInt

class ScrollbarHelper(
    private val recyclerView: RecyclerView,
    private val track: View,
    private val thumb: View,
    private val touchArea: View
) {
    private var isDragging = false
    private var dragStartY = 0f
    private var initialThumbTop = 0f

    private val scrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) update()
        }
    }

    private fun getDensity() = recyclerView.context.resources.displayMetrics.density
    private fun getTopPadding() = 16 * getDensity()
    private fun getTrackHeight(): Float {
        val visibleHeight = recyclerView.height.toFloat()
        return (visibleHeight - getTopPadding() * 2).coerceAtLeast(0f)
    }
    private fun getThumbHeight(): Float {
        val totalHeight = recyclerView.computeVerticalScrollRange().toFloat()
        val visibleHeight = recyclerView.height.toFloat()
        if (totalHeight <= 0f) return 40f
        return (visibleHeight / totalHeight * getTrackHeight()).coerceAtLeast(40f)
    }

    private fun scrollToEnd() {
        val itemCount = recyclerView.adapter?.itemCount ?: return
        if (itemCount == 0) return
        (recyclerView.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(itemCount - 1, 0)
    }

    init {
        recyclerView.addOnScrollListener(scrollListener)

        recyclerView.post { update() }

        touchArea.setOnTouchListener { _, event ->
            try {
                val totalHeight = recyclerView.computeVerticalScrollRange().toFloat()
                val visibleHeight = recyclerView.height.toFloat()
                val scrollRange = (totalHeight - visibleHeight).coerceAtLeast(0f)
                val topPadding = getTopPadding()
                val thumbHeight = getThumbHeight()
                val trackRange = (getTrackHeight() - thumbHeight).coerceAtLeast(1f)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true
                        dragStartY = event.rawY
                        initialThumbTop = (thumb.layoutParams as? FrameLayout.LayoutParams)
                            ?.topMargin?.toFloat() ?: topPadding
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val newThumbTop = (initialThumbTop + (event.rawY - dragStartY))
                            .coerceIn(topPadding, topPadding + trackRange)

                        if (newThumbTop >= topPadding + trackRange - 1f) {
                            scrollToEnd()
                        } else {
                            val scrollRatio = (newThumbTop - topPadding) / trackRange
                            val newOffset = (scrollRatio * scrollRange)
                                .roundToInt()
                                .coerceIn(0, scrollRange.toInt())
                            recyclerView.scrollBy(
                                0,
                                newOffset - recyclerView.computeVerticalScrollOffset()
                            )
                        }
                        update()
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isDragging = false
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                isDragging = false
                false
            }
        }
    }

    fun update() {
        try {
            val totalHeight = recyclerView.computeVerticalScrollRange().toFloat()
            val visibleHeight = recyclerView.height.toFloat()
            val scrollOffset = recyclerView.computeVerticalScrollOffset().toFloat()

            if (totalHeight <= visibleHeight) {
                track.visibility = View.GONE
                thumb.visibility = View.GONE
                return
            }

            track.visibility = View.VISIBLE
            thumb.visibility = View.VISIBLE

            val topPadding = getTopPadding()
            val thumbHeight = getThumbHeight()
            val trackRange = (getTrackHeight() - thumbHeight).coerceAtLeast(1f)
            val scrollRange = (totalHeight - visibleHeight).coerceAtLeast(1f)

            val thumbTop = (topPadding + (scrollOffset / scrollRange) * trackRange)
                .coerceIn(topPadding, topPadding + trackRange)

            val params = thumb.layoutParams as? FrameLayout.LayoutParams ?: return
            params.height = thumbHeight.toInt()
            params.topMargin = thumbTop.toInt()
            thumb.layoutParams = params
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun destroy() {
        recyclerView.removeOnScrollListener(scrollListener)
    }
}
