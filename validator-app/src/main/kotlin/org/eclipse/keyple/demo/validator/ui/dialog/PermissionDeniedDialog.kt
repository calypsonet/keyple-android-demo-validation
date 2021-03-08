/*
 * Copyright (c) 2021 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.keyple.demo.validator.ui.dialog

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.eclipse.keyple.demo.validator.R

/**
 *  @author youssefamrani
 */

class PermissionDeniedDialog  : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val builder = AlertDialog.Builder(it)
            builder
                .setCancelable(false)
                .setMessage(R.string.permission_denied_message)
                .setPositiveButton(android.R.string.cancel) { _, _ ->
                    dismiss()
                    it.finish()
                }
            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }
}