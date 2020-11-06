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
package org.eclipse.keyple.famoco.validator.data

import android.app.Activity
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardService
import org.eclipse.keyple.core.service.event.ObservableReader
import org.eclipse.keyple.core.service.exception.KeypleException
import org.eclipse.keyple.core.service.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.core.service.exception.KeyplePluginNotFoundException
import org.eclipse.keyple.core.service.exception.KeypleReaderIOException
import org.eclipse.keyple.famoco.validator.di.scopes.AppScoped
import org.eclipse.keyple.famoco.validator.reader.IReaderRepository
import org.eclipse.keyple.famoco.validator.ticketing.TicketingSession
import org.eclipse.keyple.famoco.validator.ticketing.TicketingSessionManager
import timber.log.Timber
import javax.inject.Inject

@AppScoped
class CardReaderApi @Inject constructor(private var readerRepository: IReaderRepository) {

    private lateinit var ticketingSessionManager: TicketingSessionManager
    private var ticketingSession: TicketingSession? = null

    @Throws(
        KeyplePluginInstantiationException::class,
        IllegalStateException::class,
        KeyplePluginNotFoundException::class
    )
    suspend fun init(observer: ObservableReader.ReaderObserver?) {

        /*
         * Register plugin
         */
        try {
            readerRepository.registerPlugin()
        } catch (e: KeypleException) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        }

        /*
         * Init PO reader
         */
        val poReader: Reader?
        try {
            poReader = readerRepository.initPoReader()
        } catch (e: KeyplePluginNotFoundException) {
            Timber.e(e)
            throw IllegalStateException("PoReader with name AndroidCoppernicAskPlugin was not found")
        } catch (e: KeypleReaderIOException) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        } catch (e: KeypleException) {
            Timber.e(e)
            throw IllegalStateException(e.message)
        }

        /*
         * Init SAM reader
         */
        var samReaders: Map<String, Reader>? = null
        try {
            samReaders = readerRepository.initSamReaders()
        } catch (e: KeyplePluginNotFoundException) {
            Timber.e(e)
        }
        if (samReaders.isNullOrEmpty()) {
            Timber.w("No SAM reader available")
        }

        poReader.let { reader ->
            /* remove the observer if it already exist */
            (reader as ObservableReader).addObserver(observer)

            ticketingSessionManager = TicketingSessionManager()

            ticketingSession =
                ticketingSessionManager.createTicketingSession(readerRepository) as TicketingSession
        }
    }

    fun startNfcDetection(activity: Activity) {
        readerRepository.enableNfcReaderMode(activity)

        /*
        * Provide the Reader with the selection operation to be processed when a PO is
        * inserted.
        */
        ticketingSession?.prepareAndSetPoDefaultSelection()

        (readerRepository.poReader as ObservableReader).startCardDetection(ObservableReader.PollingMode.REPEATING)
    }

    fun stopNfcDetection(activity: Activity) {
        try {
            // notify reader that se detection has been switched off
            (readerRepository.poReader as ObservableReader).stopCardDetection()
            // Disable Reader Mode for NFC Adapter
            readerRepository.disableNfcReaderMode(activity)
        } catch (e: KeyplePluginNotFoundException) {
            Timber.e(e, "NFC Plugin not found")
        }
    }

    fun getTicketingSession(): TicketingSession? {
        return ticketingSession
    }

    fun onDestroy(observer: ObservableReader.ReaderObserver?) {
        if (observer != null && readerRepository.poReader != null) {
            (readerRepository.poReader as ObservableReader).removeObserver(observer)
        }
        SmartCardService.getInstance().plugins.forEach {
            SmartCardService.getInstance().unregisterPlugin(it.key)
        }
        SmartCardService.getInstance().plugins.clear()
        readerRepository.onDestroy()
        ticketingSession = null
    }

    fun isMockedResponse(): Boolean {
        return readerRepository.isMockedResponse()
    }
}
