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
    private val handler = Handler(Looper.getMainLooper())
    private var retryCount = 0

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

        // Search through all launchable apps
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
        retryCount = 0

        val pm = packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(launchIntent)

            // Step 1: Wait for WhatsApp to open and initiate contact search
            handler.postDelayed({
                automationStep = AutomationStep.SEARCHING_CONTACT
                rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
            }, 1000)
        }
    }

    private fun handleWhatsAppAutomation(root: AccessibilityNodeInfo) {
        when (automationStep) {
            AutomationStep.SEARCHING_CONTACT -> {
                // Find Search Icon / Button
                val searchButtons = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/menuitem_search")
                if (searchButtons.isNotEmpty()) {
                    searchButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    handler.postDelayed({
                        typeContactName(rootInActiveWindow ?: root)
                    }, 400)
                } else {
                    val searchNode = findNodeByTextOrDesc(root, "Search") ?: findNodeByTextOrDesc(root, "தேடு")
                    if (searchNode != null) {
                        clickNodeOrParent(searchNode)
                        handler.postDelayed({
                            typeContactName(rootInActiveWindow ?: root)
                        }, 400)
                    } else if (retryCount < 5) {
                        retryCount++
                        handler.postDelayed({
                            rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
                        }, 400)
                    }
                }
            }

            AutomationStep.OPENING_CHAT -> {
                // Find and click the contact name in the search results list
                val contactNode = findNodeByTextOrDesc(root, pendingContactName ?: "")
                if (contactNode != null) {
                    clickNodeOrParent(contactNode)
                    automationStep = AutomationStep.TYPING_MESSAGE
                    retryCount = 0
                    handler.postDelayed({
                        rootInActiveWindow?.let { typeAndSendTextMessage(it) }
                    }, 900)
                } else if (retryCount < 5) {
                    retryCount++
                    handler.postDelayed({
                        rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
                    }, 500)
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
            retryCount = 0

            handler.postDelayed({
                rootInActiveWindow?.let { handleWhatsAppAutomation(it) }
            }, 700)
        }
    }

    private fun typeAndSendTextMessage(root: AccessibilityNodeInfo) {
        // Find WhatsApp message text entry field
        val entryBoxes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/entry")
        if (entryBoxes.isNotEmpty()) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pendingMessageText)
            }
            entryBoxes[0].performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            // Click Send Button
            handler.postDelayed({
                val sendButtons = (rootInActiveWindow ?: root).findAccessibilityNodeInfosByViewId("com.whatsapp:id/send")
                if (sendButtons.isNotEmpty()) {
                    sendButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "WhatsApp Message successfully typed and sent by Zara!")
                    automationStep = AutomationStep.IDLE
                    pendingContactName = null
                    pendingMessageText = null
                }
            }, 400)
        } else if (retryCount < 5) {
            retryCount++
            handler.postDelayed({
                rootInActiveWindow?.let { typeAndSendTextMessage(it) }
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
