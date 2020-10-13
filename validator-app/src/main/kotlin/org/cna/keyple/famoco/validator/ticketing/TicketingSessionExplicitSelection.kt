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
package org.cna.keyple.famoco.validator.ticketing

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException
import org.eclipse.keyple.calypso.command.sam.exception.CalypsoSamCommandException
import org.eclipse.keyple.calypso.transaction.PoSelectionRequest
import org.eclipse.keyple.calypso.transaction.PoSelector
import org.eclipse.keyple.calypso.transaction.PoTransaction
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionException
import org.eclipse.keyple.core.selection.SeResource
import org.eclipse.keyple.core.selection.SeSelection
import org.eclipse.keyple.core.selection.SelectionsResult
import org.eclipse.keyple.core.seproxy.SeReader
import org.eclipse.keyple.core.seproxy.SeSelector
import org.eclipse.keyple.core.seproxy.exception.KeypleReaderException
import org.eclipse.keyple.core.seproxy.protocol.SeCommonProtocols
import timber.log.Timber

class TicketingSessionExplicitSelection(poReader: SeReader, samReader: SeReader?) : AbstractTicketingSession(poReader, samReader), ITicketingSession {
    /**
     * prepare the default selection
     */
    @Throws(KeypleReaderException::class)
    fun processExplicitSelection(): SelectionsResult {
        /*
         * Prepare a PO selection
         */
        seSelection = SeSelection()

        /* Select Calypso */
        val poSelectionRequest = PoSelectionRequest(PoSelector.builder()
            .seProtocol(SeCommonProtocols.PROTOCOL_ISO14443_4)
            .aidSelector(SeSelector.AidSelector.builder().aidToSelect(CalypsoInfo.AID).build())
            .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build())

        // Prepare the reading of the Environment and Holder file.
        poSelectionRequest.prepareReadRecordFile(CalypsoInfo.SFI_EnvironmentAndHolder, CalypsoInfo.RECORD_NUMBER_1.toInt())
        poSelectionRequest.prepareReadRecordFile(CalypsoInfo.SFI_Contracts, CalypsoInfo.RECORD_NUMBER_1.toInt())
        poSelectionRequest.prepareReadRecordFile(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1.toInt())
        poSelectionRequest.prepareReadRecordFile(CalypsoInfo.SFI_EventLog, CalypsoInfo.RECORD_NUMBER_1.toInt())

        /*
         * Add the selection case to the current selection (we could have added other cases here)
         */
        calypsoPoIndex = seSelection.prepareSelection(poSelectionRequest)
        return seSelection.processExplicitSelection(poReader)
    }

    /**
     * load the PO according to the choice provided as an argument
     *
     * @param ticketNumber
     * @return
     * @throws KeypleReaderException
     */
    @Throws(KeypleReaderException::class)
    override fun loadTickets(ticketNumber: Int): Int {
        return try {
            /**
             * Open channel (again?)
             */
            val selectionsResult = processExplicitSelection()

            /* No sucessful selection */
            if (!selectionsResult.hasActiveSelection()) {
                Timber.e("PO Not selected")
                return ITicketingSession.STATUS_SESSION_ERROR
            }

            val poTransaction =
                if (samReader != null)
                    PoTransaction(SeResource(poReader, calypsoPo), getSecuritySettings(checkSamAndOpenChannel(samReader)))
                else
                    PoTransaction(SeResource(poReader, calypsoPo))

            if (!Arrays.equals(currentPoSN, calypsoPo.applicationSerialNumberBytes)) {
                Timber.i("Load ticket status  : STATUS_CARD_SWITCHED")
                return ITicketingSession.STATUS_CARD_SWITCHED
            }

            /*
             * Open a transaction to read/write the Calypso PO
             */
            poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD)

            /*
             * Read actual ticket number
             */
            poTransaction.prepareReadRecordFile(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1.toInt())
            poTransaction.processPoCommands()

            poTransaction.prepareIncreaseCounter(CalypsoInfo.SFI_Counter, CalypsoInfo.RECORD_NUMBER_1.toInt(), ticketNumber)

            /*
             * Prepare record to be sent to Calypso PO log journal
             */
            val dateFormat: DateFormat = SimpleDateFormat("yyMMdd HH:mm:ss")
            val dateTime = dateFormat.format(Date())
            var event = ""
            event = if (ticketNumber > 0) {
                pad("$dateTime OP = +$ticketNumber", ' ', 29)
            } else {
                pad("$dateTime T1", ' ', 29)
            }

            poTransaction.prepareAppendRecord(CalypsoInfo.SFI_EventLog, event.toByteArray())

            /*
             * Process transaction
             */
            poTransaction.processClosing()

            Timber.i("Load ticket status  : STATUS_OK")
            ITicketingSession.STATUS_OK
        } catch (e: CalypsoSamCommandException) {
            e.printStackTrace()
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoCommandException) {
            e.printStackTrace()
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoTransactionException) {
            e.printStackTrace()
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: KeypleReaderException) {
            e.printStackTrace()
            ITicketingSession.STATUS_UNKNOWN_ERROR
        }
    }
}
