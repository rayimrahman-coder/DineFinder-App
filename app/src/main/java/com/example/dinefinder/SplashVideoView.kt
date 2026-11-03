package com.example.dinefinder

import android.content.Context
import android.widget.VideoView
import kotlin.math.ceil

/** Centre-crop the video to cover the window without stretching or letterboxing. */
internal class SplashVideoView(context: Context) : VideoView(context) {
    private var videoWidth = 720
    private var videoHeight = 1280
    fun videoDimensions(width: Int, height: Int) {
        if (width > 0 && height > 0) {
            videoWidth = width
            videoHeight = height
            requestLayout()
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        val scale = maxOf(width.toDouble() / videoWidth, height.toDouble() / videoHeight)
        setMeasuredDimension(ceil(videoWidth * scale).toInt(), ceil(videoHeight * scale).toInt())
    }
}
