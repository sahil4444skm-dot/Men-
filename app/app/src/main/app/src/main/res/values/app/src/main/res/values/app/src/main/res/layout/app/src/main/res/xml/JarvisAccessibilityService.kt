package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // Screen par text dhoondhkar us button ko click karta hai
    fun clickByText(texts: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        for (text in texts) {
            for (node in root.findAccessibilityNodeInfosByText(text)) {
                var n: AccessibilityNodeInfo? = node
                while (n != null && !n.isClickable) n = n.parent
                if (n != null) {
                    n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }
}
