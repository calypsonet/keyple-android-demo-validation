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
import org.calypsonet.keyple.demo.validation.databinding.ActivityHomeBinding
import org.calypsonet.keyple.demo.validation.databinding.LogoToolbarBinding

class HomeActivity : BaseActivity() {

  private lateinit var activityHomeBinding: ActivityHomeBinding
  private lateinit var logoToolbarBinding: LogoToolbarBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityHomeBinding = ActivityHomeBinding.inflate(layoutInflater)
    logoToolbarBinding = activityHomeBinding.appBarLayout
    setContentView(activityHomeBinding.root)
    setSupportActionBar(logoToolbarBinding.toolbar)
    activityHomeBinding.startBtn.setOnClickListener {
      startActivity(Intent(this, ReaderActivity::class.java))
    }
  }
}
