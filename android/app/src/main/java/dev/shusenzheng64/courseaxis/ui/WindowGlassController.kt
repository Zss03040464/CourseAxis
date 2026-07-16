package dev.shusenzheng64.courseaxis.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.Window
import android.view.WindowManager
import dev.shusenzheng64.courseaxis.core.WeekGlassVisualPolicy

object WindowGlassController {
    fun apply(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.decorView.setBackgroundColor(Color.TRANSPARENT)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && WeekGlassVisualPolicy.usesSystemBlur) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = WeekGlassVisualPolicy.systemBlurRadiusPx
                dimAmount = 0.08f
            }
        }
    }
}
