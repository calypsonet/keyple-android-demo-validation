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
package org.eclipse.keyple.famoco.validator.ticketing

import android.content.Context
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException
import org.eclipse.keyple.calypso.command.sam.exception.CalypsoSamCommandException
import org.eclipse.keyple.calypso.transaction.CalypsoPo
import org.eclipse.keyple.calypso.transaction.PoSelection
import org.eclipse.keyple.calypso.transaction.PoSelector
import org.eclipse.keyple.calypso.transaction.PoTransaction
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionException
import org.eclipse.keyple.core.card.command.AbstractApduCommandBuilder
import org.eclipse.keyple.core.card.message.CardSelectionResponse
import org.eclipse.keyple.core.card.selection.AbstractCardSelection
import org.eclipse.keyple.core.card.selection.AbstractSmartCard
import org.eclipse.keyple.core.card.selection.CardResource
import org.eclipse.keyple.core.card.selection.CardSelectionsResult
import org.eclipse.keyple.core.card.selection.CardSelectionsService
import org.eclipse.keyple.core.card.selection.CardSelector
import org.eclipse.keyple.core.card.selection.MultiSelectionProcessing
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.event.AbstractDefaultSelectionsResponse
import org.eclipse.keyple.core.service.event.ObservableReader
import org.eclipse.keyple.core.service.exception.KeypleReaderException
import org.eclipse.keyple.core.util.ByteArrayUtil
import org.eclipse.keyple.famoco.validator.models.CardReaderResponse
import org.eclipse.keyple.famoco.validator.models.Location
import org.eclipse.keyple.famoco.validator.reader.IReaderRepository
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.AID_BANKING
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.AID_HIS_STRUCTURE_5H
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.AID_NORMALIZED_IDF
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_BANKING
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_CALYPSO
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_NAVIGO
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo.PO_TYPE_NAME_OTHER
import org.eclipse.keyple.famoco.validator.ticketing.procedure.ValidationProcedure
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
        cardSelection = CardSelectionsService(MultiSelectionProcessing.FIRST_MATCH)

        /*
         * Select Calypso
         */
        val poSelectionRequest = PoSelection(
            PoSelector.builder()
                .cardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
                .aidSelector(
                    CardSelector.AidSelector.builder()
                        .aidToSelect(AID_HIS_STRUCTURE_5H).build()
                )
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build()
        )

        calypsoPoIndex = cardSelection.prepareSelection(poSelectionRequest)

        /*
         * NAVIGO
         */
        val navigoCardSelectionRequest = GenericSeSelectionRequest(
            PoSelector.builder()
                .cardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
                .aidSelector(
                    CardSelector.AidSelector.builder().aidToSelect(AID_NORMALIZED_IDF).build()
                )
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build()
        )
        navigoCardIndex = cardSelection.prepareSelection(navigoCardSelectionRequest)

        /*
         * Banking
         */
        val bankingCardSelectionRequest = GenericSeSelectionRequest(
            PoSelector.builder()
                .cardProtocol(readerRepository.getContactlessIsoProtocol()!!.applicationProtocolName)
                .aidSelector(
                    CardSelector.AidSelector.builder().aidToSelect(AID_BANKING)
                        .build()
                )
                .invalidatedPo(PoSelector.InvalidatedPo.REJECT).build()
        )
        bankingCardIndex = cardSelection.prepareSelection(bankingCardSelectionRequest)

        /*
         * Provide the Reader with the selection operation to be processed when a PO is inserted.
         */
        (poReader as ObservableReader).setDefaultSelectionRequest(
            cardSelection.defaultSelectionsRequest, ObservableReader.NotificationMode.ALWAYS
        )
    }

    fun processDefaultSelection(selectionResponse: AbstractDefaultSelectionsResponse?): CardSelectionsResult {
        Timber.i("selectionResponse = $selectionResponse")
        val selectionsResult: CardSelectionsResult =
            cardSelection.processDefaultSelectionsResponse(selectionResponse)
        if (selectionsResult.hasActiveSelection()) {
            when (selectionsResult.smartCards.keys.first()) {
                calypsoPoIndex -> {
                    calypsoPo = selectionsResult.activeSmartCard as CalypsoPo
                    poTypeName = PO_TYPE_NAME_CALYPSO
                }
                bankingCardIndex -> poTypeName = PO_TYPE_NAME_BANKING
                navigoCardIndex -> poTypeName = PO_TYPE_NAME_NAVIGO
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

    /**
     * do the personalization of the PO according to the specified profile
     *
     * @param profile
     * @return
     */
    @Throws(
        CalypsoPoTransactionException::class,
        CalypsoPoCommandException::class,
        CalypsoSamCommandException::class
    )
    fun personalize(profile: String): Boolean {
        try {
            // Should block poTransaction without Sam?
            val poTransaction = if (samReader != null)
                PoTransaction(
                    CardResource(poReader, calypsoPo),
                    getSecuritySettings(checkSamAndOpenChannel(samReader!!))
                )
            else
                PoTransaction(CardResource(poReader, calypsoPo))
            poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_PERSO)

            if ("PROFILE1" == profile) {
                poTransaction.prepareUpdateRecord(
                    CalypsoInfo.SFI_EnvironmentAndHolder,
                    CalypsoInfo.RECORD_NUMBER_1.toInt(),
                    pad("John Smith", ' ', 29).toByteArray()
                )
                poTransaction.prepareUpdateRecord(
                    CalypsoInfo.SFI_Contracts,
                    CalypsoInfo.RECORD_NUMBER_1.toInt(),
                    pad("NO CONTRACT", ' ', 29).toByteArray()
                )
            } else {
                poTransaction.prepareUpdateRecord(
                    CalypsoInfo.SFI_EnvironmentAndHolder,
                    CalypsoInfo.RECORD_NUMBER_1.toInt(),
                    pad("Harry Potter", ' ', 29).toByteArray()
                )
                poTransaction.prepareUpdateRecord(
                    CalypsoInfo.SFI_Contracts,
                    CalypsoInfo.RECORD_NUMBER_1.toInt(),
                    pad("1 MONTH SEASON TICKET", ' ', 29).toByteArray()
                )
            }
            val dateFormat: DateFormat = SimpleDateFormat("yyMMdd HH:mm:ss")
            val dateTime = dateFormat.format(Date())
            poTransaction.prepareAppendRecord(
                CalypsoInfo.SFI_EventLog,
                pad("$dateTime OP = PERSO", ' ', 29).toByteArray()
            )
            poTransaction.prepareUpdateRecord(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt(),
                ByteArrayUtil.fromHex(pad("", '0', 29 * 2))
            )
            cardSelection.prepareReleaseChannel()
            return true
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
        }
        return false
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
    @Throws(KeypleReaderException::class)
    override fun loadTickets(ticketNumber: Int): Int {
        return try {
            // Should block poTransaction without Sam?
            val poTransaction = if (samReader != null)
                PoTransaction(
                    CardResource(poReader, calypsoPo),
                    getSecuritySettings(checkSamAndOpenChannel(samReader!!))
                )
            else
                PoTransaction(CardResource(poReader, calypsoPo))

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
            poTransaction.prepareReadRecordFile(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt()
            )
            poTransaction.processPoCommands()
            poTransaction.prepareIncreaseCounter(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt(),
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
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        }
    }

    fun debitTickets(ticketNumber: Int): Int {
        return try {
            // Should block poTransaction without Sam?
            val poTransaction =
                if (samReader != null)
                    PoTransaction(
                        CardResource(poReader, calypsoPo),
                        getSecuritySettings(checkSamAndOpenChannel(samReader!!))
                    )
                else
                    PoTransaction(CardResource(poReader, calypsoPo))

            /*
             * Open a transaction to read/write the Calypso PO
             */
            poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_DEBIT)

            /* allow to determine the anticipated response */
            poTransaction.prepareReadRecordFile(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt()
            )
            poTransaction.processPoCommands()

            /*
             * Prepare decrease command
             */
            poTransaction.prepareDecreaseCounter(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt(),
                ticketNumber
            )

            /*
            * Process transaction and close session
             */
            poTransaction.processClosing()

            Timber.i("Debit ticket status  : STATUS_OK")
            ITicketingSession.STATUS_OK
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoTransactionException) {
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
    @Throws(KeypleReaderException::class)
    fun loadContract(): Int {
        return try {
            // Should block poTransaction without Sam?
            val poTransaction = if (samReader != null)
                PoTransaction(
                    CardResource(poReader, calypsoPo),
                    getSecuritySettings(checkSamAndOpenChannel(samReader!!))
                )
            else
                PoTransaction(CardResource(poReader, calypsoPo))

            if (!Arrays.equals(currentPoSN, calypsoPo.applicationSerialNumberBytes)) {
                return ITicketingSession.STATUS_CARD_SWITCHED
            }

            poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_LOAD)

            /* allow to determine the anticipated response */
            poTransaction.prepareReadRecordFile(
                CalypsoInfo.SFI_Counter,
                CalypsoInfo.RECORD_NUMBER_1.toInt()
            )
            poTransaction.processPoCommands()
            poTransaction.prepareUpdateRecord(
                CalypsoInfo.SFI_Contracts,
                CalypsoInfo.RECORD_NUMBER_1.toInt(),
                pad("1 MONTH SEASON TICKET", ' ', 29).toByteArray()
            )

            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("");
            // String dateTime = LocalDateTime.now().format(formatter);
            val dateFormat: DateFormat = SimpleDateFormat("yyMMdd HH:mm:ss")
            val event =
                pad(dateFormat.format(Date()) + " OP = +ST", ' ', 29)
            poTransaction.prepareAppendRecord(CalypsoInfo.SFI_EventLog, event.toByteArray())
            poTransaction.processClosing()
            ITicketingSession.STATUS_OK
        } catch (e: CalypsoSamCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoCommandException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        } catch (e: CalypsoPoTransactionException) {
            Timber.e(e)
            ITicketingSession.STATUS_SESSION_ERROR
        }
    }

    /**
     * Create a new class extending AbstractSeSelectionRequest
     */
    inner class GenericSeSelectionRequest(seSelector: CardSelector) :
        AbstractCardSelection<AbstractApduCommandBuilder>(seSelector) {
        override fun parse(seResponse: CardSelectionResponse): AbstractSmartCard {
            class GenericMatchingSe(
                selectionResponse: CardSelectionResponse?
            ) : AbstractSmartCard(selectionResponse)
            return GenericMatchingSe(seResponse)
        }
    }
}
