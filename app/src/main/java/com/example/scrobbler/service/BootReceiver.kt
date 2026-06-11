package com.example.scrobbler.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.scrobbler.util.Logger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Logger.i("Boot/update received — listener will rebind automatically")
            }
        }
    }
}