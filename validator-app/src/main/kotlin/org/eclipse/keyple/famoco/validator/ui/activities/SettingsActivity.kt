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
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_settings.batteryPoweredBox
import kotlinx.android.synthetic.main.activity_settings.spinnerLocationList
import kotlinx.android.synthetic.main.activity_settings.startBtn
import kotlinx.android.synthetic.main.activity_settings.timeBtn
import org.eclipse.keyple.famoco.validator.R
import org.eclipse.keyple.famoco.validator.models.KeypleSettings
import org.eclipse.keyple.famoco.validator.models.Location

class SettingsActivity : BaseActivity() {

    private var mLocationAdapter: ArrayAdapter<Location>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        setSupportActionBar(findViewById(R.id.toolbar))

        //Init location spinner
        val locations = locationFileManager.getLocations()
        mLocationAdapter = ArrayAdapter(
            this,
            R.layout.spinner_item_location, R.id.spinner_item_text, locations
        )
        spinnerLocationList.adapter = mLocationAdapter

        timeBtn.setOnClickListener {
            startActivityForResult(Intent(Settings.ACTION_DATE_SETTINGS), 0)
        }

        startBtn.setOnClickListener {
            KeypleSettings.batteryPowered = batteryPoweredBox.isChecked
            KeypleSettings.location = spinnerLocationList.selectedItem as Location
            if (KeypleSettings.batteryPowered) {
                startActivity(Intent(this, HomeActivity::class.java))
            } else {
                startActivity(Intent(this, CardReaderActivity::class.java))
            }
        }
    }
}
