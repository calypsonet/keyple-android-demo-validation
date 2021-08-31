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
package org.calypsonet.keyple.demo.validation.di.mock

import org.calypsonet.keyple.demo.validation.di.mock.MockSamReader.Companion.READER_NAME
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi

internal class MockSamReaderAdapter :
    ReaderSpi,
    MockSamReader {

    override fun transmitApdu(apduIn: ByteArray?): ByteArray {
        return apduIn ?: throw IllegalStateException("Mock no apdu in")
    }

    override fun getPowerOnData(): String {
        return ""
    }

    override fun openPhysicalChannel() {
    }

    override fun isPhysicalChannelOpen(): Boolean {
        return true
    }

    override fun checkCardPresence(): Boolean {
        return true
    }

    override fun closePhysicalChannel() {
    }

    override fun isContactless(): Boolean {
        return false
    }

    override fun getName(): String = READER_NAME

    override fun onUnregister() {
        // Do nothing
    }
}
