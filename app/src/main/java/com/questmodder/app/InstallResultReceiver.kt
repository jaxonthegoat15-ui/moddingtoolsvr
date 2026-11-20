package com.questmodder.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""
        val result = if (status == PackageInstaller.STATUS_SUCCESS) "Installation succeeded."
                     else "Installation status: $status $message"
        context.getSharedPreferences("install", Context.MODE_PRIVATE).edit().putString("last_result", result).apply()
    }
}
