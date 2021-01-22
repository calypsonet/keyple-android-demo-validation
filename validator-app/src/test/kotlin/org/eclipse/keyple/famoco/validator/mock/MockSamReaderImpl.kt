package org.eclipse.keyple.famoco.validator.mock

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