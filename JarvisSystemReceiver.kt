package com.jarvis.supreme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build

/**
 * Listens for:
 *  1. Device Boot Completed -> Automatically launches Jarvis and welcomes Sir.
 *  2. Battery 100% -> Announces "Sir, charge complete. Battery is at 100 percent."
 *  3. Battery Low (<15%) -> Warns "Sir, battery is critically low at X percent. Please connect the charger."
 */
class JarvisSystemReceiver : BroadcastReceiver() {

    private var announcedFullCharge = false
    private var announcedLowBattery = false

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val svcIntent = Intent(context, JarvisWakeWordService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svcIntent)
                } else {
                    context.startService(svcIntent)
                }
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val isFull = status == BatteryManager.BATTERY_STATUS_FULL || (level == scale && level == 100)

                if (isFull && !announcedFullCharge) {
                    announcedFullCharge = true
                    JarvisWakeWordService.speakOut(
                        "Sir, charge complete. Battery is at 100 percent. Please unplug the charger."
                    )
                } else if (level in 1..95) {
                    announcedFullCharge = false
                }

                if (level in 1..15 && !isCharging && !announcedLowBattery) {
                    announcedLowBattery = true
                    JarvisWakeWordService.speakOut(
                        "Sir, battery is low at $level percent. Please plug in your charger."
                    )
                } else if (level > 20 || isCharging) {
                    announcedLowBattery = false
                }
            }
        }
    }
}
