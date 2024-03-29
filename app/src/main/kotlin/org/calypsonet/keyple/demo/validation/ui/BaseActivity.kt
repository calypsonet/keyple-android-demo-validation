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

import android.widget.Toast
import dagger.android.support.DaggerAppCompatActivity
import javax.inject.Inject
import org.calypsonet.keyple.demo.validation.data.LocationRepository
import org.calypsonet.keyple.demo.validation.domain.TicketingService

abstract class BaseActivity : DaggerAppCompatActivity() {

  @Inject lateinit var ticketingService: TicketingService
  @Inject lateinit var locationRepository: LocationRepository

  fun showToast(message: String) {
    runOnUiThread { Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show() }
  }
}
