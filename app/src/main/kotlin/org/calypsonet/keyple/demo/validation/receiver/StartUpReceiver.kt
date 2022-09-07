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
package org.calypsonet.keyple.demo.validation.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.calypsonet.keyple.demo.validation.ui.activities.SplashScreenActivity

/**
 * This class is used only on the Flowbird (Axio2) device. You can use it if you want to launch this
 * Demo app when the device starts up.
 */
class StartUpReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    startApp(context)
  }

  private fun startApp(context: Context) {
    val dialogIntent = Intent(context, SplashScreenActivity::class.java)
    dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(dialogIntent)
  }
}
