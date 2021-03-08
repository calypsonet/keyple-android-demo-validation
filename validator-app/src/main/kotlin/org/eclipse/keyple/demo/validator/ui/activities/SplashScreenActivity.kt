/*
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */


package org.eclipse.keyple.demo.validator.ui.activities

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import org.eclipse.keyple.demo.validator.R
import org.eclipse.keyple.demo.validator.di.scopes.ActivityScoped
import org.eclipse.keyple.demo.validator.reader.IReaderRepository
import org.eclipse.keyple.demo.validator.ui.dialog.PermissionDeniedDialog
import org.eclipse.keyple.demo.validator.util.PermissionHelper
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@ActivityScoped
class SplashScreenActivity : BaseActivity() {

    @Inject
    lateinit var readerRepository: IReaderRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // Make sure this is before calling super.onCreate
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splashscreen)

        val permissions = mutableListOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if(!readerRepository.getPermissions().isNullOrEmpty()){
            permissions.addAll(readerRepository.getPermissions()!!)
        }

        val granted = PermissionHelper.checkPermission(
            this,
            permissions.toTypedArray()
        )

        if (granted) {
            Timer().schedule(object : TimerTask() {
                override fun run() {
                    if (!isFinishing) {
                        startActivity(Intent(applicationContext, SettingsActivity::class.java))
                        finish()
                    }
                }
            }, SPLASH_MAX_DELAY_MS.toLong())
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PermissionHelper.MY_PERMISSIONS_REQUEST_ALL -> {
                val storagePermissionGranted = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                if (storagePermissionGranted) {
                    startActivity(Intent(applicationContext, SettingsActivity::class.java))
                    finish()
                } else {
                    PermissionDeniedDialog()
                        .apply {
                            show(
                                supportFragmentManager,
                                PermissionDeniedDialog::class.java.simpleName
                            )
                        }
                }
                return
            }

            // Add other 'when' lines to check for other
            // permissions this app might request.
            else -> {
                // Ignore all other requests.
            }
        }
    }

    companion object {
        private const val SPLASH_MAX_DELAY_MS = 2000
    }
}