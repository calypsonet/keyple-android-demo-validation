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
package org.calypsonet.keyple.demo.validation.reader

import android.app.Activity
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.resource.spi.ReaderConfiguratorSpi

/**
 *  @author youssefamrani
 */

interface IReaderRepository {

    var poReader: Reader?
    var samReaders: MutableList<Reader>

    fun registerPlugin(activity: Activity)

    suspend fun initPoReader(): Reader?

    suspend fun initSamReaders(): List<Reader>

    fun getSamReader(): Reader?
    fun getContactlessIsoProtocol(): PoReaderProtocol?
    fun getSamReaderProtocol(): String?
    fun getPlugin(): Plugin
    fun clear()
    fun getSamRegex(): String
    fun getReaderConfiguratorSpi(): ReaderConfiguratorSpi?

    fun isMockedResponse(): Boolean = false

    fun getPermissions(): Array<String>? = null

    /**
     * Method used only for Flowbird device. Changes color and sound if needed
     */
    fun displayWaiting(): Boolean = false

    /**
     * Method used only for Flowbird device. Changes color and sound if needed
     */
    fun displayResultSuccess(): Boolean = false

    /**
     * Method used only for Flowbird device. Changes color and sound if needed
     */
    fun displayResultFailed(): Boolean = false
}

data class PoReaderProtocol(val readerProtocolName: String, val applicationProtocolName: String)
