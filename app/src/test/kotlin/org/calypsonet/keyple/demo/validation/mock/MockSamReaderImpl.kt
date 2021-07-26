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
package org.calypsonet.keyple.demo.validation.mock

import org.eclipse.keyple.core.plugin.AbstractLocalReader

/**
 *  @author youssefamrani
 */

@Suppress("INVISIBLE_ABSTRACT_MEMBER_FROM_SUPER_WARNING")
class MockSamReaderImpl : AbstractLocalReader(
    "",
    ""
) {

    override fun transmitApdu(apduIn: ByteArray?): ByteArray {
        return apduIn ?: throw IllegalStateException("Mock no apdu in")
    }

    override fun getATR(): ByteArray? {
        return null
    }

    override fun openPhysicalChannel() {
    }

    override fun isPhysicalChannelOpen(): Boolean {
        return true
    }

    override fun isCardPresent(): Boolean {
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

    override fun isCurrentProtocol(readerProtocolName: String?): Boolean {
        return true
    }

    override fun deactivateReaderProtocol(readerProtocolName: String?) {
        // Do nothing
    }

    override fun activateReaderProtocol(readerProtocolName: String?) {
        // Do nothing
    }

    companion object {
        const val READER_NAME = "Mock_Sam"
    }
}
