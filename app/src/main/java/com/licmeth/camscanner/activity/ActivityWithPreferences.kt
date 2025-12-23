package com.licmeth.camscanner.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.licmeth.camscanner.helper.UserPreferences

open class ActivityWithPreferences : AppCompatActivity() {

    private lateinit var preferences: UserPreferences

    open override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = UserPreferences(applicationContext)
    }
}