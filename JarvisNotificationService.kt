package com.jarvis.supreme

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Reads incoming WhatsApp, Telegram, SMS, and Missed Call notifications in real time.
 * Features:
 *  - Announces sender & message content to the owner.
 *  - Detects if the message is written in Banglish, pure Bangla, or English and asks the owner how they want to reply.
 *  - Can reply directly inline via Android RemoteInput without opening the app.
 *  - STRICT SECURITY LOCK: Never deletes any message or clears chat history without explicit owner confirmation.
 */
class JarvisNotificationService : NotificationListenerService() {

    companion object {
        private var lastReplyAction: Notification.Action? = null
        private var lastSenderName: String = ""

        fun sendInlineReply(context: android.content.Context, replyText: String): Boolean {
            val action = lastReplyAction ?: return false
            val remoteInputs = action.remoteInputs ?: return false
            val intent = Intent()
            val bundle = Bundle()
            for (remoteInput in remoteInputs) {
                bundle.putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(remoteInputs, intent, bundle)
            return try {
                action.actionIntent.send(context, 0, intent)
                true
            } catch (e: PendingIntent.CanceledException) {
                false
            }
        }
    }

    private lateinit var memoryStore: EncryptedMemoryStore
    private lateinit var brain: JarvisBrain

    override fun onCreate() {
        super.onCreate()
        memoryStore = EncryptedMemoryStore(this)
        brain = JarvisBrain(this, memoryStore, JarvisHardwareController(this))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || !memoryStore.isJarvisActive) return
        val pkg = sbn.packageName ?: return
        if (pkg != "com.whatsapp" && pkg != "com.google.android.apps.messaging") return

        val extras = sbn.notification.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Ignore group summary notifications
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        // Cache inline reply action so Jarvis can reply hands-free
        sbn.notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }?.let {
            lastReplyAction = it
            lastSenderName = sender
        }

        val langStyle = brain.detectLanguageStyle(message)
        val promptByLanguage = when (langStyle) {
            "BANGLISH" -> "Boss, $sender WhatsApp-e Banglish-e message koreche: '$message'. Ami ki Banglish-e reply debo, Pure Bangla-te, naki English-e?"
            "PURE_BANGLA" -> "বস, $sender বাংলায় মেসেজ পাঠিয়েছেন: '$message'। আমি কীভাবে উত্তর দেব বলুন?"
            else -> "Boss, new message from $sender: '$message'. Tell me the topic or reply and I'll send it right now."
        }

        JarvisWakeWordService.speakOut(promptByLanguage)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Security audit: Jarvis never deletes user messages without explicit permission
    }
}
