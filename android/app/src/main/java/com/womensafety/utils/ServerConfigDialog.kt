package com.womensafety.utils

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.womensafety.R
import com.womensafety.data.api.ApiClient

object ServerConfigDialog {

    fun show(context: Context, onServerUpdated: (() -> Unit)? = null) {
        val currentHost = Constants.getServerHost(context)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_server_config, null)
        val etHost = dialogView.findViewById<EditText>(R.id.etServerHost)
        val tvCurrent = dialogView.findViewById<TextView>(R.id.tvCurrentDetected)
        val btnWifi = dialogView.findViewById<Button>(R.id.btnPresetWifi)
        val btnUsb = dialogView.findViewById<Button>(R.id.btnPresetUsb)
        val btnEmulator = dialogView.findViewById<Button>(R.id.btnPresetEmulator)

        etHost.setText(currentHost)
        val isEmu = Constants.isEmulator()
        tvCurrent.text = "Environment: ${if (isEmu) "Android Emulator" else "Physical Mobile Device"}"

        btnWifi.setOnClickListener {
            etHost.setText(Constants.DEFAULT_LAN_HOST)
        }

        btnUsb.setOnClickListener {
            etHost.setText(Constants.USB_REVERSE_HOST)
        }

        btnEmulator.setOnClickListener {
            etHost.setText(Constants.DEFAULT_EMULATOR_HOST)
        }

        AlertDialog.Builder(context)
            .setTitle("⚙️ Server Connection Settings")
            .setView(dialogView)
            .setPositiveButton("Save & Connect") { _, _ ->
                val newHost = etHost.text.toString().trim()
                if (newHost.isNotBlank()) {
                    Constants.setCustomServerHost(context, newHost)
                    ApiClient.reset()
                    Toast.makeText(context, "Server set to: $newHost", Toast.LENGTH_SHORT).show()
                    onServerUpdated?.invoke()
                }
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Reset Default") { _, _ ->
                context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .remove(Constants.KEY_CUSTOM_SERVER_HOST)
                    .apply()
                ApiClient.reset()
                val def = Constants.getServerHost(context)
                Toast.makeText(context, "Reset to default: $def", Toast.LENGTH_SHORT).show()
                onServerUpdated?.invoke()
            }
            .show()
    }
}
