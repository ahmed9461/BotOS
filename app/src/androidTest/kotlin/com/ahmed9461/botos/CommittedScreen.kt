package com.ahmed9461.botos

import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.view.inspector.WindowInspector
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Device evidence only: wait for a submitted frame, never manufacture or repaint a screenshot. */
internal fun captureCommittedScreen(): Bitmap {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    check(android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
        "Screen capture must run on the instrumentation thread"
    }
    instrumentation.waitForIdleSync()
    if (Build.VERSION.SDK_INT >= 29) awaitFocusedFrame()
    // A frame-commit callback guarantees submission, not visibility in the compositor.
    val deadline = SystemClock.uptimeMillis() + 3_000L
    do {
        SystemClock.sleep(150L)
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: error("Device screenshot unavailable")
        val colors = HashSet<Int>(32)
        for (y in bitmap.height / 20 until bitmap.height * 24 / 25 step 6) {
            for (x in 0 until bitmap.width step 6) {
                colors += bitmap.getPixel(x, y)
                if (colors.size > 16) return bitmap
            }
        }
        bitmap.recycle()
    } while (SystemClock.uptimeMillis() < deadline)
    error("Screenshot remained a blank frame after rendering; visual evidence rejected")
}

@RequiresApi(29)
private fun awaitFocusedFrame() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    var root: View? = null
    val deadline = SystemClock.uptimeMillis() + 5_000L
    do {
        instrumentation.runOnMainSync {
            root = WindowInspector.getGlobalWindowViews().lastOrNull {
                it.isAttachedToWindow && it.isShown && it.hasWindowFocus() && it.isHardwareAccelerated
            }
        }
        if (root == null) SystemClock.sleep(50L)
    } while (root == null && SystemClock.uptimeMillis() < deadline)
    val focused = checkNotNull(root) { "No focused hardware-rendered test window" }
    val committed = CountDownLatch(1)
    val callback = Runnable { committed.countDown() }
    var observer: ViewTreeObserver? = null
    instrumentation.runOnMainSync {
        check(focused.isAttachedToWindow) { "Test window detached before capture" }
        observer = focused.viewTreeObserver
        checkNotNull(observer).registerFrameCommitCallback(callback)
        focused.invalidate()
    }
    try {
        check(committed.await(5, TimeUnit.SECONDS)) { "Test window did not commit a rendered frame" }
    } finally {
        instrumentation.runOnMainSync {
            observer?.takeIf { it.isAlive }?.unregisterFrameCommitCallback(callback)
        }
    }
}
