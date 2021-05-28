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
package org.eclipse.keyple.demo.validator.ticketing

import android.content.Context
import org.eclipse.keyple.card.calypso.CalypsoExtensionServiceProvider
import org.eclipse.keyple.card.calypso.card.CalypsoCard
import org.eclipse.keyple.card.calypso.transaction.CardTransactionService
import org.eclipse.keyple.core.service.CardSelectionServiceFactory
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.selection.CardSelectionResult
import org.eclipse.keyple.core.service.selection.CardSelector
import org.eclipse.keyple.core.service.selection.MultiSelectionProcessing
import org.eclipse.keyple.core.service.selection.ScheduledCardSelectionsResponse
import org.eclipse.keyple.demo.validator.models.CardReaderResponse
import org.eclipse.keyple.demo.validator.models.Location
import org.eclipse.keyple.demo.validator.reader.IReaderRepository
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.AID_HIS_STRUCTURE_5H
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.AID_NORMALIZED_IDF
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_BANKING
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_CALYPSO_05H
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_NAVIGO
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_OTHER
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.RECORD_NUMBER_1
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Contracts
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_EventLog
import org.eclipse.keyple.demo.validator.ticketing.procedure.ValidationProcedure
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date

class TicketingSession(readerRepository: IReaderRepository) :
    AbstractTicketingSession(readerRepository), ITicketingSession {

    private var bankingCardIndex = 0
    private var navigoCardIndex = 0

    private var samReader: Reader? = null

    init {
        samReader = readerRepository.getSamReader()
    }

    /*
     * Should be instanciated through the ticketing session mananger
    */
    init {
        prepareAndSetPoDefaultSelection()
    }

    /**
     * prepare the default selection
     */
    fun prepareAndSetPoDefaultSelection() {
        /*
         * Prepare a PO selection
         */
        cardSelection = CardSelectionServiceFactory.getService(MultiSelectionProcessing.FIRST_MATCH)
        calypsoCardExtensionProvider = CalypsoExtensionServiceProvider.getService()

        /*
         * Select Calypso
         */
        val poSelectionRequest =
            calypsoCardExtensionProvider.createCardSelection(
                CardSelector.builder()
                    .filterByDfName(AID_HIS_STRUCTURE_5H)
                    .filterByCardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
                    .build(),
                false
            )

        /*
         * Add the selection case to the current selection
         */
        calypsoPoIndex = cardSelection.prepareSelection(poSelectionRequest)

        /*
         * NAVIGO
         */
        val navigoCardSelectionRequest =
            calypsoCardExtensionProvider.createCardSelection(
                CardSelector.builder()
                    .filterByDfName(AID_NORMALIZED_IDF)
                    .filterByCardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
                    .build(),
                false
            )
        navigoCardIndex = cardSelection.prepareSelection(navigoCardSelectionRequest)


        /*
         * Banking
         */
//        val bankingCardSelectionRequest = GenericSeSelectionRequest(
//            PoSelector.builder()
//                .cardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
//                .aidSelector(
//                    CardSelector.AidSelector.builder().aidToSelect(AID_BANKING)
//                        .build()
//                )
//                .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build()
//        )
//        bankingCardIndex = cardSelection.prepareSelection(bankingCardSelectionRequest)

        /*
         * Provide the Reader with the selection operation to be processed when a PO is inserted.
         */
        cardSelection.scheduleCardSelectionScenario(
            poReader as ObservableReader,
            ObservableReader.NotificationMode.ALWAYS
        )
    }

    fun processDefaultSelection(selectionResponse: ScheduledCardSelectionsResponse): CardSelectionResult {
        Timber.i("selectionResponse = $selectionResponse")
        val selectionsResult =
            cardSelection.parseScheduledCardSelectionsResponse(selectionResponse)

        if (selectionsResult.hasActiveSelection()) {
            when (selectionsResult.smartCards.keys.first()) {
                calypsoPoIndex -> {
                    calypsoPo = selectionsResult.activeSmartCard as CalypsoCard
                    poTypeName = PO_TYPE_NAME_CALYPSO_05H
                }
                navigoCardIndex -> {
                    calypsoPo = selectionsResult.activeSmartCard as CalypsoCard
                    poTypeName = PO_TYPE_NAME_NAVIGO
                }
                bankingCardIndex -> poTypeName = PO_TYPE_NAME_BANKING
                else -> poTypeName = PO_TYPE_NAME_OTHER
            }
        }

        Timber.i("PO type = $poTypeName")
        return selectionsResult
    }


    fun launchValidationProcedure(context: Context, locations: List<Location>): CardReaderResponse {
        return ValidationProcedure().launch(
            context = context,
            validationAmount = 1,
            locations = locations,
            calypsoPo = calypsoPo,
            samReader = samReader,
            ticketingSession = this
        )
    }

    /*
     * public void forceCloseChannel() throws KeypleReaderException {
     * logger.debug("Force close logical channel (hack for nfc reader)"); List<ApduRequest>
     * requestList = new ArrayList<>(); ((ProxyReader)poReader).transmit(new
     * SeRequest(requestList)); }
     */
    /**
     * load the PO according to the choice provided as an argument
     *
     * @param ticketNumber
     * @return
     * @throws KeypleReaderException
     */
    @Throws(Exception::class)
    override fun loadTickets(ticketNumber: Int): Int {
        return try {
            // Should block poTransaction without Sam?
            val poTransaction = if (samReader != null) {
                //TODO: remove useless code
                calypsoCardExtensionProvider.createCardTransaction(
                    poReader,
                    calypsoPo,
                    getSecuritySettings()
                )
            } else {
                calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                    poReader,
                    calypsoPo
                )
            }
            if (!Arrays.equals(currentPoSN, calypsoPo.applicationSerialNumberBytes)) {
                Timber.i("Load ticket status  : STATUS_CARD_SWITCHED")
                return ITicketingSession.STATUS_CARD_SWITCHED
            }

            if (!Arrays.equals(currentPoSN, calypsoPo.applicationSerialNumberBytes)) {
                Timber.i("Load ticket status  : STATUS_CARD_SWITCHED")
                return ITicketingSession.STATUS_CARD_SWITCHED
            }
            /*
             * Open a transaction to read/write the Calypso PO
             */
            poTransaction.processOpening(CardTransactionService.SessionAccessLevel.SESSION_LVL_LOAD)

            /*
             * Read actual ticket number
             */
            poTransaction.prepareReadRecordFile(
                SFI_Counter,
                RECORD_NUMBER_1.toInt()
            )
            poTransaction.processCardCommands()
            poTransaction.prepareIncreaseCounter(
                SFI_Counter,
                RECORD_NUMBER_1.toInt(),
                ticketNumber
            )

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
            cardSelection.prepareReleaseChannel()
            Timber.i("Load ticket status  : STATUS_OK")
            ITicketingSession.STATUS_OK
        } catch (e: Exception) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        }
    }

    fun debitTickets(ticketNumber: Int): Int {
        return try {
            // Should block poTransaction without Sam?
            val poTransaction = if (samReader != null) {
                //TODO: remove useless code
                calypsoCardExtensionProvider.createCardTransaction(
                    poReader,
                    calypsoPo,
                    getSecuritySettings()
                )
            } else {
                calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                    poReader,
                    calypsoPo
                )
            }

            /*
             * Open a transaction to read/write the Calypso PO
             */
            poTransaction.processOpening(CardTransactionService.SessionAccessLevel.SESSION_LVL_DEBIT)

            /* allow to determine the anticipated response */
            poTransaction.prepareReadRecordFile(
                SFI_Counter,
                RECORD_NUMBER_1.toInt()
            )
            poTransaction.processCardCommands()

            /*
             * Prepare decrease command
             */
            poTransaction.prepareDecreaseCounter(
                SFI_Counter,
                RECORD_NUMBER_1.toInt(),
                ticketNumber
            )

            /*
            * Process transaction and close session
             */
            poTransaction.processClosing()

            Timber.i("Debit ticket status  : STATUS_OK")
            ITicketingSession.STATUS_OK
        } catch (e: Exception) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        }
    }


    /**
     * Load a season ticket contract
     *
     * @return
     * @throws KeypleReaderException
     */
    @Throws(Exception::class)
    fun loadContract(): Int {
        return try {
            val poTransaction = if (samReader != null) {
                //TODO: remove useless code
                calypsoCardExtensionProvider.createCardTransaction(
                    poReader,
                    calypsoPo,
                    getSecuritySettings()
                )
            } else {
                calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                    poReader,
                    calypsoPo
                )
            }

            if (!Arrays.equals(currentPoSN, calypsoPo.applicationSerialNumberBytes)) {
                return ITicketingSession.STATUS_CARD_SWITCHED
            }

            poTransaction.processOpening(CardTransactionService.SessionAccessLevel.SESSION_LVL_LOAD)

            /* allow to determine the anticipated response */
            poTransaction.prepareReadRecordFile(
                SFI_Counter,
                RECORD_NUMBER_1.toInt()
            )
            poTransaction.processCardCommands()
            poTransaction.prepareUpdateRecord(
                SFI_Contracts,
                RECORD_NUMBER_1.toInt(),
                pad("1 MONTH SEASON TICKET", ' ', 29).toByteArray()
            )

            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("")
            // String dateTime = LocalDateTime.now().format(formatter)
            val dateFormat: DateFormat = SimpleDateFormat("yyMMdd HH:mm:ss")
            val event =
                pad(dateFormat.format(Date()) + " OP = +ST", ' ', 29)
            poTransaction.prepareAppendRecord(SFI_EventLog, event.toByteArray())
            poTransaction.processClosing()
            ITicketingSession.STATUS_OK
        } catch (e: Exception) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        }
    }

//    /**
//     * Create a new class extending AbstractSeSelectionRequest
//     */
//    inner class GenericSeSelectionRequest(seSelector: CardSelector) :
//        AbstractCardSelection<AbstractApduCommandBuilder>(seSelector) {
//        override fun parse(seResponse: CardSelectionResponse): AbstractSmartCard {
//            class GenericMatchingSe(
//                selectionResponse: CardSelectionResponse?
//            ) : AbstractSmartCard(selectionResponse)
//            return GenericMatchingSe(seResponse)
//        }
//    }
}
