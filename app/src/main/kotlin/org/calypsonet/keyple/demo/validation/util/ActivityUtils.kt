/********************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.calypsonet.keyple.demo.validation.util

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import dagger.internal.Preconditions

object ActivityUtils {
    /**
     * The fragment is added to the container view with id frameId. The operation is
     * performed by the fragmentManager.
     */
    fun addFragmentToActivity(fragmentManager: FragmentManager, fragment: Fragment, frameId: Int) {
        Preconditions.checkNotNull(
            fragmentManager
        )
        Preconditions.checkNotNull(fragment)
        val transaction =
            fragmentManager.beginTransaction()
        transaction.replace(frameId, fragment)
        transaction.addToBackStack(null)
        transaction.commit()
    }
}
