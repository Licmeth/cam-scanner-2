package com.licmeth.camscanner.activity

import android.os.Bundle
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.licmeth.camscanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        private const val PREFS_NAME = "CamScannerPrefs"
        private const val KEY_IMAGE_QUALITY = "image_quality"
        private const val KEY_SENSITIVITY = "sensitivity"
        private const val KEY_AUTO_CAPTURE = "auto_capture"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load saved settings
        binding.qualitySeekbar.progress = prefs.getInt(KEY_IMAGE_QUALITY, 85)
        binding.sensitivitySeekbar.progress = prefs.getInt(KEY_SENSITIVITY, 50)
        binding.autoCaptureCheckbox.isChecked = prefs.getBoolean(KEY_AUTO_CAPTURE, false)

        // Setup listeners
        binding.qualitySeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt(KEY_IMAGE_QUALITY, progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.sensitivitySeekbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                prefs.edit().putInt(KEY_SENSITIVITY, progress).apply()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        binding.autoCaptureCheckbox.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(KEY_AUTO_CAPTURE, isChecked).apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}