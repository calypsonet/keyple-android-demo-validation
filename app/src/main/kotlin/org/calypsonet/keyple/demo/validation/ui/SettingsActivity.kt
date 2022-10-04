/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.demo.validation.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import kotlinx.android.synthetic.main.activity_settings.app_version
import kotlinx.android.synthetic.main.activity_settings.batteryPoweredBox
import kotlinx.android.synthetic.main.activity_settings.spinnerLocationList
import kotlinx.android.synthetic.main.activity_settings.startBtn
import kotlinx.android.synthetic.main.activity_settings.timeBtn
import org.calypsonet.keyple.demo.validation.BuildConfig
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.AppSettings
import org.calypsonet.keyple.demo.validation.data.model.Location

class SettingsActivity : BaseActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_settings)
    setSupportActionBar(findViewById(R.id.toolbar))
    // Init location spinner
    val locations = locationRepository.getLocations()
    val locationsAdapter =
        ArrayAdapter(this, R.layout.spinner_item_location, R.id.spinner_item_text, locations)
    spinnerLocationList.adapter = locationsAdapter
    timeBtn.setOnClickListener { startActivityForResult(Intent(Settings.ACTION_DATE_SETTINGS), 0) }
    startBtn.setOnClickListener {
      AppSettings.location = spinnerLocationList.selectedItem as Location
      AppSettings.batteryPowered = batteryPoweredBox.isChecked
      if (AppSettings.batteryPowered) {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
      } else {
        startActivity(Intent(this, ReaderActivity::class.java))
      }
    }
    app_version.text = getString(R.string.version, BuildConfig.VERSION_NAME)
  }
}
