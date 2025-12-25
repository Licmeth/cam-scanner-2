package com.licmeth.camscanner.activity

import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.lifecycleScope
import com.licmeth.camscanner.databinding.ActivitySettingsBinding
import com.licmeth.camscanner.helper.UserPreferences
import com.licmeth.camscanner.model.DebugOutputLevel
import kotlinx.coroutines.launch

class SettingsActivity : ActivityWithPreferences() {

    private lateinit var binding: ActivitySettingsBinding
    private var isCallbacksDisabled = false

    companion object {
        private const val TAG = "SettingsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupUI()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun setupUI() {
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupDebugOutputSwitch()
        setupDebugOutputLevel()
    }

    private fun setupDebugOutputSwitch() {
        // Add callback
        binding.debugOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            handleValueChange(UserPreferences.KEY_ENABLE_DEBUG_OVERLAY, isChecked)
        }

        // Observe preferences and update UI
        lifecycleScope.launch {
            preferences.enableDebugOverlay.collect { enabled ->
                // prevent triggering the listener while programmatically updating
                isCallbacksDisabled = true
                binding.debugOverlaySwitch.isChecked = enabled
                isCallbacksDisabled = false
            }
        }
    }

    private fun setupDebugOutputLevel() {
        // Add callback
        binding.settingsDebugOutputLevelHolder.setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity)
                .setTitle("Debug output level")
                .setSingleChoiceItems(
                    DebugOutputLevel.entries.map { it.name }.toTypedArray(),
                    DebugOutputLevel.entries.indexOfFirst { it.name == binding.settingsDebugOutputLevelValue.text.toString() },
                    DialogInterface.OnClickListener { dialog, which ->
                        val selectedLevel = DebugOutputLevel.entries[which]
                        // save selected value
                        lifecycleScope.launch {
                            preferences.setDebugOutputLevel(selectedLevel)
                        }
                        dialog.dismiss()
                    }
                )
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Observe preferences and update UI
        lifecycleScope.launch {
            preferences.debugOutputLevel.collect { outputLevel ->
                // prevent triggering the listener while programmatically updating
                isCallbacksDisabled = true
                binding.settingsDebugOutputLevelValue.text = outputLevel.name
                isCallbacksDisabled = false
            }
        }
    }

    private fun <T> handleValueChange(key: Preferences.Key<T>, value: T) {
        if (isCallbacksDisabled) return

        when (key) {
            UserPreferences.KEY_ENABLE_DEBUG_OVERLAY -> {
                lifecycleScope.launch {
                    preferences.setEnableDebugOverlay(value as Boolean)
                }
            }
            else -> {
                Log.e(TAG, "Unknown preference key: $key")
                Toast.makeText(this, "Error: Unknown preference key: $key", Toast.LENGTH_LONG).show()
            }
        }
    }
}