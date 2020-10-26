/********************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.eclipse.keyple.famoco.validator.ui.activities

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_settings.batteryPoweredBox
import kotlinx.android.synthetic.main.activity_settings.locationEdit
import kotlinx.android.synthetic.main.activity_settings.startBtn
import kotlinx.android.synthetic.main.activity_settings.timeBtn
import kotlinx.android.synthetic.main.logo_toolbar.toolbarLogo
import org.eclipse.keyple.famoco.validator.BuildConfig
import org.eclipse.keyple.famoco.validator.R
import org.eclipse.keyple.famoco.validator.util.KeypleSettings

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))
        toolbarLogo.setImageResource(R.drawable.ic_logo_blue)

        timeBtn.setOnClickListener {
            startActivityForResult(Intent(Settings.ACTION_DATE_SETTINGS), 0)
        }

        startBtn.setOnClickListener {
            KeypleSettings.location = locationEdit.text.toString()
            KeypleSettings.batteryPowered = batteryPoweredBox.isChecked
            if (!KeypleSettings.location.isNullOrBlank()) {
                if (KeypleSettings.batteryPowered) {
                    startActivity(Intent(this, HomeActivity::class.java))
                } else {
                    startActivity(Intent(this, CardReaderActivity::class.java))
                }
            } else {
                Toast.makeText(this, R.string.msg_location_empty, Toast.LENGTH_LONG).show()
            }
        }

        if (BuildConfig.DEBUG) {
            locationEdit.text = Editable.Factory.getInstance().newEditable("Paris")
        }
    }
}
