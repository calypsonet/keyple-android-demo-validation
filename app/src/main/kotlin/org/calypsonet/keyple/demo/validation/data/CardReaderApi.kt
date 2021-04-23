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
package org.calypsonet.keyple.demo.validation.data

import android.app.Activity
import org.eclipse.keyple.core.card.ReaderCommunicationException
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.spi.ReaderObserverSpi
import org.eclipse.keyple.demo.validator.di.scopes.AppScoped
import org.eclipse.keyple.demo.validator.reader.IReaderRepository
import org.eclipse.keyple.demo.validator.ticketing.TicketingSession
import org.eclipse.keyple.demo.validator.ticketing.TicketingSessionManager
import timber.log.Timber
import javax.inject.Inject

@AppScoped
class CardReaderApi @Inject constructor(private var readerRepository: IReaderRepository) {

    private var ticketingSession: ITicketingSession? = null

    var readersInitialized = false

    @Throws(
        KeyplePluginException::class,
        IllegalStateException::class,
        Exception::class
    )
    suspend fun init(observer: ReaderObserverSpi?, activity: Activity) {

        /*
         * Register plugin
         */
        try {
            readerRepository.registerPlugin(activity)
        } catch (e: Exception) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        }

        /*
         * Init PO reader
         */
        val poReader: Reader?
        try {
            poReader = readerRepository.initPoReader()
        } catch (e: KeyplePluginException) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        } catch (e: ReaderCommunicationException) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        } catch (e: Exception) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        }

        /*
         * Init SAM reader
         */
        var samReaders: List<Reader>? = null
        try {
            samReaders = readerRepository.initSamReaders()
        } catch (e: KeyplePluginException) {
            Timber.e(e)
        } catch (e: Exception) {
            Timber.e(e)
        }
        if (samReaders.isNullOrEmpty()) {
            Timber.w("No SAM reader available")
        }

        poReader.let { reader ->
            /* remove the observer if it already exist */
            (reader as ObservableReader).addObserver(observer)

            ticketingSession = TicketingSession(readerRepository)
        }
    }

    fun startNfcDetection() {
        /*
        * Provide the Reader with the selection operation to be processed when a PO is
        * inserted.
        */
        ticketingSession?.prepareAndSetPoDefaultSelection()

        (readerRepository.poReader as ObservableReader).startCardDetection(ObservableReader.PollingMode.REPEATING)
    }

    fun stopNfcDetection() {
        try {
            // notify reader that se detection has been switched off
            (readerRepository.poReader as ObservableReader).stopCardDetection()
        } catch (e: KeyplePluginException) {
            Timber.e(e, "NFC Plugin not found")
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    fun getTicketingSession(): ITicketingSession? {
        return ticketingSession
    }

    fun onDestroy(observer: ReaderObserverSpi?) {
        readerRepository.clear()
        if (observer != null && readerRepository.poReader != null) {
            (readerRepository.poReader as ObservableReader).removeObserver(observer)
        }

        val smartCardService = SmartCardServiceProvider.getService()
        smartCardService.plugins.forEach {
            smartCardService.unregisterPlugin(it.name)
        }

        ticketingSession = null
    }

    fun isMockedResponse(): Boolean {
        return readerRepository.isMockedResponse()
    }

    fun displayResultSuccess(): Boolean = readerRepository.displayResultSuccess()

    fun displayResultFailed(): Boolean = readerRepository.displayResultFailed()

}
