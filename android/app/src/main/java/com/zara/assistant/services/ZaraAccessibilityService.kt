package com.zara.assistant.services

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ZaraAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ZaraAccessibilityService? = null
        private const val TAG = "ZaraAccessibility"
    }

    private var pendingContactName: String? = null
    private var pendingMessageText: String? = null
    private var automationStep = AutomationStep.IDLE

    enum class AutomationStep {
        IDLE,
        OPENING_WHATSAPP,
        SEARCHING_CONTACT,
        OPENING_CHAT,
        TYPING_MESSAGE,
        SENDING_MESSAGE
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Zara Accessibility Service Connected!")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val packageName = event?.packageName?.toString() ?: ""

        if (packageName.contains("whatsapp") && automationStep != AutomationStep.IDLE) {
            handleWhatsAppAutomation(rootNode)
        }
    }

    override fun onInterrupt() {
        automationStep = AutomationStep.IDLE
    }

    /**
     * Launch ANY app on the phone by its spoken name (Tamil or English)
     */
    fun launchAppByName(appName: String): Boolean {
        val pm = packageManager
        val cleanName = appName.lowercase().trim()

        // Known direct package mappings for popular apps
        val directPackages = mapOf(
            "insta" to "com.instagram.android",
            "instagram" to "com.instagram.android",
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "camera" to "com.google.android.GoogleCamera",
            "spotify" to "com.spotify.music",
            "settings" to "com.android.settings",
            "zomato" to "com.application.zomato",
            "swiggy" to "in.swiggy.android",
            "paytm" to "net.one97.paytm",
            "gpay" to "com.google.android.apps.nbu.paisa.user"
        )

        for ((key, pkg) in directPackages) {
            if (cleanName.contains(key)) {
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return true
                }
            }
        }

        // Fuzzy match across ALL installed apps
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val appList = pm.queryIntentActivities(mainIntent, 0)
        for (resolveInfo in appList) {
            val label = resolveInfo.loadLabel(pm).toString().lowercase()
            if (label.contains(cleanName) || cleanName.contains(label)) {
                val launchIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(launchIntent)
                    return true
                }
            }
        }

        return false
    }

    /**
     * Automate WhatsApp: Open -> Search Contact -> Type Message -> Click Send
     */
    fun sendWhatsAppMessage(contact: String, message: String) {
        pendingContactName = contact
        pendingMessageText = message
        automationStep = AutomationStep.OPENING_WHATSAPP

        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)

            // Transition to searching contact after WhatsApp loads
            Handler(Looper.getMainLooper()).postDelayed({
                automationStep = AutomationStep.SEARCHING_CONTACT
                rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
            }, 1200)
        }
    }

    private fun handleWhatsAppAutomation(root: AccessibilityNodeInfo) {
        when (automationStep) {
            AutomationStep.SEARCHING_CONTACT -> {
                // Find Search button in WhatsApp
                val searchButtons = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/menuitem_search")
                if (searchButtons.isNotEmpty()) {
                    searchButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Handler(Looper.getMainLooper()).postDelayed({
                        typeContactName(rootInActiveWindow ?: root)
                    }, 500)
                } else {
                    // Try by content description
                    findNodeByTextOrDesc(root, "Search")?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Handler(Looper.getMainLooper()).postDelayed({
                        typeContactName(rootInActiveWindow ?: root)
                    }, 500)
                }
            }

            AutomationStep.OPENING_CHAT -> {
                // Click the first contact row in search results
                val contactNode = findNodeByTextOrDesc(root, pendingContactName ?: "")
                if (contactNode != null) {
                    clickNodeOrParent(contactNode)
                    automationStep = AutomationStep.TYPING_MESSAGE
                    Handler(Looper.getMainLooper()).postDelayed({
                        rootInActiveWindow?.let { typeAndSendTextMessage(it) }
                    }, 1000)
                }
            }

            AutomationStep.TYPING_MESSAGE -> {
                typeAndSendTextMessage(root)
            }

            else -> {}
        }
    }

    private fun typeContactName(root: AccessibilityNodeInfo) {
        val searchBoxes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/search_src_text")
        if (searchBoxes.isNotEmpty()) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pendingContactName)
            }
            searchBoxes[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            automationStep = AutomationStep.OPENING_CHAT

            Handler(Looper.getMainLooper()).postDelayed({
                rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
            }, 800)
        }
    }

    private fun typeAndSendTextMessage(root: AccessibilityNodeInfo) {
        val entryBoxes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
        if (entryBoxes.isNotEmpty()) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pendingMessageText)
            }
            entryBoxes[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            // Click Send Button
            Handler(Looper.getMainLooper()).postDelayed({
                val sendButtons = (rootInActiveWindow ?: root).findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    automationStep = AutomationStep.IDLE
                    pendingContactName = null
                    pendingMessageText = null
                    Log.d(TAG, "WhatsApp Message sent successfully via Zara!")
                }
            }, 400)
        }
    }

    private fun findNodeByTextOrDesc(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (node == null || query.isBlank()) return null
        if (node.text?.toString()?.contains(query, ignoreCase = true) == true) return node
        if (node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true) return node

        for (i in 0 until node.childCount) {
            val found = findNodeByTextOrDesc(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        return false
    }
}
