package com.example.scrobbler

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.scrobbler.api.LastFmApiClient
import com.example.scrobbler.databinding.ActivitySetupBinding
import com.example.scrobbler.util.Prefs
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private val prefs by lazy { Prefs.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        refreshUiState()

        binding.btnGrantNotificationAccess.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        binding.btnBatteryOptimisation.setOnClickListener {
            requestBatteryOptimisationExemption()
        }

        binding.btnSaveCredentials.setOnClickListener {
            saveAndAuthenticate()
        }

        binding.btnClearAuth.setOnClickListener {
            prefs.sessionKey = ""
            refreshUiState()
            Toast.makeText(this, "Cleared session key", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUiState()
    }

    private fun refreshUiState() {
        val hasNls = hasNotificationAccess()
        binding.tvNlsStatus.text = if (hasNls) "✓ Granted" else "✗ Not granted"
        binding.tvNlsStatus.setTextColor(
            if (hasNls) getColor(R.color.success) else getColor(R.color.error)
        )

        val authed = prefs.isAuthenticated
        binding.tvAuthStatus.text = if (authed) "✓ Authenticated as ${prefs.username}"
        else "✗ Not authenticated"
        binding.tvAuthStatus.setTextColor(
            if (authed) getColor(R.color.success) else getColor(R.color.error)
        )

        binding.etApiKey.setText(prefs.apiKey)
        binding.etSharedSecret.setText(prefs.sharedSecret)
        binding.etUsername.setText(prefs.username)
        binding.btnClearAuth.visibility = if (authed) View.VISIBLE else View.GONE

        val exempt = isBatteryOptimisationExempt()
        binding.tvBatteryStatus.text = if (exempt) "✓ Exempt"
        else "✗ Not exempt (recommended for Samsung)"
        binding.tvBatteryStatus.setTextColor(
            if (exempt) getColor(R.color.success) else getColor(R.color.warning)
        )
    }

    private fun hasNotificationAccess(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        return enabled.contains(packageName)
    }

    private fun isBatteryOptimisationExempt(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimisationExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun saveAndAuthenticate() {
        val apiKey = binding.etApiKey.text.toString().trim()
        val secret = binding.etSharedSecret.text.toString().trim()
        val user   = binding.etUsername.text.toString().trim()
        val pass   = binding.etPassword.text.toString()

        if (apiKey.isBlank() || secret.isBlank() || user.isBlank() || pass.isBlank()) {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.apiKey       = apiKey
        prefs.sharedSecret = secret
        prefs.username     = user

        binding.btnSaveCredentials.isEnabled = false
        binding.btnSaveCredentials.text = "Authenticating…"

        lifecycleScope.launch {
            val client     = LastFmApiClient(apiKey, secret)
            val sessionKey = client.authenticate(user, pass)

            runOnUiThread {
                binding.btnSaveCredentials.isEnabled = true
                binding.btnSaveCredentials.text = "Save & Authenticate"

                if (sessionKey != null) {
                    prefs.sessionKey = sessionKey
                    Toast.makeText(this@SetupActivity, "Authenticated!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@SetupActivity, "Authentication failed. Check credentials.", Toast.LENGTH_LONG).show()
                }
                refreshUiState()
            }
        }
    }
}