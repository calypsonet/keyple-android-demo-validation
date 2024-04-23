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
import org.calypsonet.keyple.demo.validation.BuildConfig
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.AppSettings
import org.calypsonet.keyple.demo.validation.data.model.Location
import org.calypsonet.keyple.demo.validation.databinding.ActivitySettingsBinding
import org.calypsonet.keyple.demo.validation.databinding.LogoToolbarBinding

class SettingsActivity : BaseActivity() {

  private lateinit var activitySettingsBinding: ActivitySettingsBinding
  private lateinit var logoToolbarBinding: LogoToolbarBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activitySettingsBinding = ActivitySettingsBinding.inflate(layoutInflater)
    logoToolbarBinding = activitySettingsBinding.appBarLayout
    setContentView(activitySettingsBinding.root)
    setSupportActionBar(logoToolbarBinding.toolbar)
    // Init location spinner
    val locations = locationRepository.getLocations()
    val locationsAdapter =
        ArrayAdapter(this, R.layout.spinner_item_location, R.id.spinner_item_text, locations)
    activitySettingsBinding.spinnerLocationList.adapter = locationsAdapter
    activitySettingsBinding.timeBtn.setOnClickListener {
      startActivityForResult(Intent(Settings.ACTION_DATE_SETTINGS), 0)
    }
    activitySettingsBinding.startBtn.setOnClickListener {
      AppSettings.location = activitySettingsBinding.spinnerLocationList.selectedItem as Location
      AppSettings.batteryPowered = activitySettingsBinding.batteryPoweredBox.isChecked
      if (AppSettings.batteryPowered) {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
      } else {
        startActivity(Intent(this, ReaderActivity::class.java))
      }
    }
    activitySettingsBinding.appVersion.text = getString(R.string.version, BuildConfig.VERSION_NAME)
  }
}
