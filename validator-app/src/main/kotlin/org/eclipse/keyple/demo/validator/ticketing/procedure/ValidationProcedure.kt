/*
 * Copyright (c) 2021 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.eclipse.keyple.demo.validator.ticketing.procedure

import android.content.Context
import org.eclipse.keyple.calypso.command.po.exception.CalypsoPoCommandException
import org.eclipse.keyple.calypso.command.sam.exception.CalypsoSamCommandException
import org.eclipse.keyple.calypso.transaction.CalypsoPo
import org.eclipse.keyple.calypso.transaction.PoTransaction
import org.eclipse.keyple.calypso.transaction.exception.CalypsoPoTransactionException
import org.eclipse.keyple.core.card.selection.CardResource
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.demo.validator.R
import org.eclipse.keyple.demo.validator.exception.ContractVersionNumberErrorException
import org.eclipse.keyple.demo.validator.exception.EnvironmentException
import org.eclipse.keyple.demo.validator.exception.EnvironmentExceptionKey
import org.eclipse.keyple.demo.validator.exception.EventException
import org.eclipse.keyple.demo.validator.exception.EventExceptionKey
import org.eclipse.keyple.demo.validator.exception.NoContractAvailableException
import org.eclipse.keyple.demo.validator.exception.NoLocationDefinedException
import org.eclipse.keyple.demo.validator.exception.NoSamForValidationException
import org.eclipse.keyple.demo.validator.exception.ValidationException
import org.eclipse.keyple.demo.validator.models.CardReaderResponse
import org.eclipse.keyple.demo.validator.models.KeypleSettings
import org.eclipse.keyple.demo.validator.models.Location
import org.eclipse.keyple.demo.validator.models.Status
import org.eclipse.keyple.demo.validator.models.Validation
import org.eclipse.keyple.demo.validator.models.mapper.ValidationMapper
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.RECORD_NUMBER_1
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.RECORD_NUMBER_2
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.RECORD_NUMBER_3
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.RECORD_NUMBER_4
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Contracts
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter_0A
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter_0B
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter_0C
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_Counter_0D
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_EnvironmentAndHolder
import org.eclipse.keyple.demo.validator.ticketing.CalypsoInfo.SFI_EventLog
import org.eclipse.keyple.demo.validator.ticketing.ITicketingSession
import org.eclipse.keyple.parser.keyple.ContractStructureParser
import org.eclipse.keyple.parser.keyple.CounterStructureParser
import org.eclipse.keyple.parser.keyple.EnvironmentHolderStructureParser
import org.eclipse.keyple.parser.keyple.EventStructureParser
import org.eclipse.keyple.parser.model.EventStructureDto
import org.eclipse.keyple.parser.model.type.ContractPriorityEnum
import org.eclipse.keyple.parser.model.type.VersionNumberEnum
import org.eclipse.keyple.parser.utils.DateUtils
import org.joda.time.DateTime
import timber.log.Timber
import java.util.Calendar
import java.util.Date

/**
 *  @author youssefamrani
 */
class ValidationProcedure {

    fun launch(
        context: Context,
        validationAmount: Int,
        locations: List<Location>,
        calypsoPo: CalypsoPo,
        samReader: Reader?,
        ticketingSession: ITicketingSession
    ): CardReaderResponse {
        val now = DateTime.now()
//        val now = DateTime()
//            .withTimeAtStartOfDay()
//            .withYear(2021)
//            .withMonthOfYear(1)
//            .withDayOfMonth(14)
//            .withHourOfDay(15)
//            .withMinuteOfHour(30)

        var status: Status = Status.LOADING
        var errorMessage: String? = null
        val poTransaction: PoTransaction?
        var eventDate: Date? = null
        var passValidityEndDate: Date? = null
        var nbTicketsLeft: Int? = null
        var validation: Validation? = null

        /*
         * Step 1 - Open a Validation session reading the environment record.
         */
        poTransaction =
            try {
                if (samReader != null) {
                    val cardResource = ticketingSession.checkSamAndOpenChannel(samReader)
                    PoTransaction(
                        CardResource(ticketingSession.poReader, calypsoPo),
                        ticketingSession.getSecuritySettings(cardResource)
                    )
                } else {
                    throw NoSamForValidationException()
                }
            } catch (e: Exception) {
                Timber.w(e)
                status = Status.ERROR
                errorMessage = e.message
                null
            }

        if (poTransaction != null) {
            try {

                /*******************
                 * Event and Environment Analysis
                 *******************/
                /*
                 * Step 2 - Unpack environment structure from the binary present in the environment record.
                 */
                poTransaction.prepareReadRecordFile(
                    SFI_EnvironmentAndHolder,
                    RECORD_NUMBER_1.toInt()
                )

                /*
                 * Open a transaction to read/write the Calypso PO and read the Environment file
                 */
                poTransaction.processOpening(PoTransaction.SessionSetting.AccessLevel.SESSION_LVL_DEBIT)

                val efEnvironmentHolder =
                    calypsoPo.getFileBySfi(SFI_EnvironmentAndHolder)
                val env = EnvironmentHolderStructureParser().parse(efEnvironmentHolder.data.content)

                /*
                 * Step 3 - If EnvVersionNumber of the Environment structure is not the expected one (==1 for the current version) reject the card.
                 * <Abort Transaction if inTransactionFlag is true and exit process>
                 */
                if (env.envVersionNumber != VersionNumberEnum.CURRENT_VERSION.key) {
                    status = Status.INVALID_CARD
                    throw EnvironmentException(EnvironmentExceptionKey.WRONG_VERSION_NUMBER)
                }

                /*
                 * Step 4 - If EnvEndDate points to a date in the past reject the card.
                 * <Abort Transaction if inTransactionFlag is true and exit process>
                 */
                val envEndDate = DateTime(env.getEnvEndDateAsDate())
                if (envEndDate.isBefore(now)) {
                    status = Status.INVALID_CARD
                    throw EnvironmentException(EnvironmentExceptionKey.EXPIRED)
                }

                /*
                 * Step 5 - Read and unpack the last event record.
                 */
                poTransaction.prepareReadRecordFile(
                    SFI_EventLog,
                    RECORD_NUMBER_1.toInt()
                )
                poTransaction.processPoCommands()

                val efEventLog = calypsoPo.getFileBySfi(SFI_EventLog)
                val event = EventStructureParser().parse(efEventLog.data.content)

                /*
                 * Step 6 - If EventVersionNumber is not the expected one (==1 for the current version) reject the card.
                 * <Abort Transaction and exit process>
                 */
                val eventVersionNumber = event.eventVersionNumber

                if (eventVersionNumber != VersionNumberEnum.CURRENT_VERSION.key) {
                    if (eventVersionNumber == VersionNumberEnum.UNDEFINED.key) {
                        status = Status.EMPTY_CARD
                        throw EventException(EventExceptionKey.CLEAN_CARD)
                    } else {
                        status = Status.INVALID_CARD
                        throw EventException(EventExceptionKey.WRONG_VERSION_NUMBER)
                    }
                }

                /*
                 * Step 7 - Store the ContractPriority fields in a persistent object.
                 */
                val contractPriorities = mutableListOf<Pair<Int, ContractPriorityEnum>>()


                /*******************
                 * Best Contract Search
                 *******************/
                /*
                 * Step 7 - Create a list of ContractPriority fields that are different from 0 or 31.
                 */
                if (event.contractPriority1 != ContractPriorityEnum.FORBIDDEN &&
                    event.contractPriority1 != ContractPriorityEnum.EXPIRED
                ) {
                    contractPriorities.add(Pair(1, event.contractPriority1))
                }
                if (event.contractPriority2 != ContractPriorityEnum.FORBIDDEN &&
                    event.contractPriority2 != ContractPriorityEnum.EXPIRED
                ) {
                    contractPriorities.add(Pair(2, event.contractPriority2))
                }
                if (event.contractPriority3 != ContractPriorityEnum.FORBIDDEN &&
                    event.contractPriority3 != ContractPriorityEnum.EXPIRED
                ) {
                    contractPriorities.add(Pair(3, event.contractPriority3))
                }
                if (event.contractPriority4 != ContractPriorityEnum.FORBIDDEN &&
                    event.contractPriority4 != ContractPriorityEnum.EXPIRED
                ) {
                    contractPriorities.add(Pair(4, event.contractPriority4))
                }

                if (contractPriorities.isEmpty()) {
                    /*
                     * Step 9 - If the list is empty go to END.
                     */
                    status = Status.EMPTY_CARD
                    throw NoContractAvailableException()
                }


                var priority1 = event.contractPriority1
                var priority2 = event.contractPriority2
                var priority3 = event.contractPriority3
                var priority4 = event.contractPriority4
                var contractUsed = 0
                var writeEvent = false

                /*
                 * Step 10 - For each element in the list:
                 */
                val sortedPriorities = contractPriorities.toList().sortedBy {
                    it.second.key
                }

                /*
                 * Step 11 - For each element in the list:
                 */
                for (it in sortedPriorities) {
                    val record = it.first
                    val contractPriority = it.second

                    /*
                     * Step 11.1 - Read and unpack the contract record for the index being iterated.
                     */
                    poTransaction.prepareReadRecordFile(
                        SFI_Contracts,
                        record
                    )

                    poTransaction.processPoCommands()

                    val efContractParser = calypsoPo.getFileBySfi(SFI_Contracts)
                    val dataContent = efContractParser.data.allRecordsContent[record]!!
                    val contract = ContractStructureParser().parse(dataContent)

                    /*
                     * Step 11.2 - If ContractVersionNumber is not the expected one (==1 for the current version) reject the card.
                     * <Abort Transaction and exit process>
                     */
                    if (contract.contractVersionNumber != VersionNumberEnum.CURRENT_VERSION) {
                        status = Status.INVALID_CARD
                        throw ContractVersionNumberErrorException()
                    }

                    /*
                     * Step 11.3 - '  If ContractAuthenticator is not 0 perform the verification of the value
                     * by using the PSO Verify Signature command of the SAM.
                     */
                    @Suppress("ControlFlowWithEmptyBody")
                    if (contract.contractAuthenticator != 0) {
                        /*
                         * Step 11.3.1 - If the value is wrong reject the card.
                         * <Abort Transaction if inTransactionFlag is true and exit process>
                         */
                        /*
                         * Step 11.3.2 - If the value of ContractSaleSam is present in the SAM Black List reject the card.
                         * <Abort Transaction if inTransactionFlag is true and exit process>
                         */
                        //TODO: steps 11.3.1 & 11.3.2
                    }

                    /*
                     * Step 11.4 - If ContractValidityEndDate points to a date in the past update the associated ContractPriorty field
                     * present in the persistent object to 31 and move to the next element in the list
                     */
                    val contractValidityEndDate =
                        DateTime(contract.getContractValidityEndDateAsDate())
                    if (contractValidityEndDate.isBefore(now)) {
                        when (record) {
                            RECORD_NUMBER_1.toInt() -> priority1 = ContractPriorityEnum.EXPIRED
                            RECORD_NUMBER_2.toInt() -> priority2 = ContractPriorityEnum.EXPIRED
                            RECORD_NUMBER_3.toInt() -> priority3 = ContractPriorityEnum.EXPIRED
                            RECORD_NUMBER_4.toInt() -> priority4 = ContractPriorityEnum.EXPIRED
                        }
                        status = Status.EMPTY_CARD
                        errorMessage = context.getString(R.string.expired_title)
                        writeEvent = true
                        continue
                    }

                    /*
                     * Step 11.5 - If the ContractTariff value for the contract read is 2 or 3:
                     */
                    if (contractPriority == ContractPriorityEnum.MULTI_TRIP ||
                        contractPriority == ContractPriorityEnum.STORED_VALUE
                    ) {
                        /*
                         * Step 11.5.1 - Read and unpack the counter associated to the contract (1st counter for Contract #1 and so forth).
                         */
//                        printCounterValues(poTransaction, calypsoPo)

                        val counterSfi = when (record) {
                            RECORD_NUMBER_1.toInt() -> SFI_Counter_0A
                            RECORD_NUMBER_2.toInt() -> SFI_Counter_0B
                            RECORD_NUMBER_3.toInt() -> SFI_Counter_0C
                            RECORD_NUMBER_4.toInt() -> SFI_Counter_0D
                            else -> throw IllegalStateException("Unhandled counter record number : $record")
                        }

                        poTransaction.prepareReadCounterFile(
                            counterSfi,
                            RECORD_NUMBER_1.toInt()
                        )
                        poTransaction.processPoCommands()

                        val efCounter = calypsoPo.getFileBySfi(counterSfi)
                        val counterContent = efCounter.data.allRecordsContent[1]!!
                        val counterValue =
                            CounterStructureParser().parse(counterContent).counterValue

                        /*
                         * Step 11.5.2 - If the counter value is 0 update the associated ContractPriorty field
                         * present in the persistent object to 31 and move to the next element in the list
                         */
                        if (counterValue == 0) {
                            when (record) {
                                RECORD_NUMBER_1.toInt() -> priority1 = ContractPriorityEnum.EXPIRED
                                RECORD_NUMBER_2.toInt() -> priority2 = ContractPriorityEnum.EXPIRED
                                RECORD_NUMBER_3.toInt() -> priority3 = ContractPriorityEnum.EXPIRED
                                RECORD_NUMBER_4.toInt() -> priority4 = ContractPriorityEnum.EXPIRED
                            }
                            status = Status.EMPTY_CARD
                            errorMessage = context.getString(R.string.no_trips_left)
                            writeEvent = true
                            continue
                        }
                        /*
                         * Step 11.5.3 - If the counter value is > 0 && ContractTariff == 3 && CounterValue < ValidationAmount
                         * move to the next element in the list
                         */
                        else if (counterValue > 0 &&
                            contractPriority == ContractPriorityEnum.STORED_VALUE &&
                            counterValue < validationAmount
                        ) {
                            status = Status.EMPTY_CARD
                            errorMessage = context.getString(R.string.no_trips_left)
                            continue
                        }
                        /*
                         * Step 11.5.4 - UPDATE COUNTER
                         * Decrement the counter value by the appropriate amount
                         * (1 if ContractTariff is 2, and the configured value for the trip if ContractTariff is 3).
                         */
                        else {
                            val decrement = when (contractPriority) {
                                ContractPriorityEnum.MULTI_TRIP -> SINGLE_VALIDATION_AMOUNT
                                ContractPriorityEnum.STORED_VALUE -> validationAmount
                                else -> 0
                            }
                            if (decrement > 0) {
                                poTransaction.prepareDecreaseCounter(
                                    SFI_Counter,
                                    record,
                                    decrement
                                )

                                poTransaction.processPoCommands()
                                nbTicketsLeft = counterValue - decrement

//                                //TODO: check with Ludo
//                                if(nbTicketsLeft == 0){
//                                    //TODO: change contract priority to 31
//                                    when (record) {
//                                        RECORD_NUMBER_1.toInt() -> priority1 = ContractPriorityEnum.EXPIRED
//                                        RECORD_NUMBER_2.toInt() -> priority2 = ContractPriorityEnum.EXPIRED
//                                        RECORD_NUMBER_3.toInt() -> priority3 = ContractPriorityEnum.EXPIRED
//                                        RECORD_NUMBER_4.toInt() -> priority4 = ContractPriorityEnum.EXPIRED
//                                    }
//                                }
                            }
                        }
                    } else if (contractPriority == ContractPriorityEnum.SEASON_PASS) {
                        passValidityEndDate = contract.getContractValidityEndDateAsDate()
                    }

                    /*
                     * We will create a new event for this contract
                     */
                    contractUsed = record
                    writeEvent = true
                    break
                }

                if (writeEvent) {

                    /*
                     * Step 12 - Fill the event structure to update:
                     */
                    if (KeypleSettings.location == null) {
                        throw NoLocationDefinedException()
                    }

                    val eventToWrite: EventStructureDto
                    if (contractUsed > 0) {
                        /*
                         * Create a new validation event
                         */

                        val calendar = Calendar.getInstance()
                        calendar.time = now.toDate()
                        calendar.set(Calendar.MILLISECOND, 0)
                        eventDate = calendar.time
                        eventToWrite = EventStructureDto(
                            eventVersionNumber = VersionNumberEnum.CURRENT_VERSION.key,
                            eventDateStamp = DateUtils.dateToDateCompact(eventDate),
                            eventTimeStamp = DateUtils.dateToTimeCompact(eventDate),
                            eventLocation = KeypleSettings.location!!.id,
                            eventContractUsed = contractUsed,
                            contractPriority1 = priority1,
                            contractPriority2 = priority2,
                            contractPriority3 = priority3,
                            contractPriority4 = priority4
                        )
                        validation = ValidationMapper.map(eventToWrite, locations)

                        Timber.i("Validation procedure result : SUCCESS")
                        status = Status.SUCCESS
                        errorMessage = null
                    } else {
                        /*
                         * Update old event's priorities
                         */
                        eventToWrite = EventStructureDto(
                            eventVersionNumber = event.eventVersionNumber,
                            eventDateStamp = event.eventDateStamp,
                            eventTimeStamp = event.eventTimeStamp,
                            eventLocation = event.eventLocation,
                            eventContractUsed = event.eventContractUsed,
                            contractPriority1 = priority1,
                            contractPriority2 = priority2,
                            contractPriority3 = priority3,
                            contractPriority4 = priority4
                        )
                    }

                    /*
                     * Step 13 - Pack the Event structure and append it to the event file
                     */
                    val eventBytesToWrite = EventStructureParser().generate(eventToWrite)
                    poTransaction.prepareUpdateRecord(
                        SFI_EventLog,
                        RECORD_NUMBER_1.toInt(),
                        eventBytesToWrite
                    )
                    poTransaction.processPoCommands()

                } else {
                    Timber.i("Validation procedure result : Failed - No valid contract found")
                    if (errorMessage.isNullOrEmpty()) {
                        errorMessage = context.getString(R.string.no_valid_title_detected)
                    }
                }

            } catch (e: ValidationException) {
                Timber.e(e)
                errorMessage = e.message
            } catch (e: CalypsoSamCommandException) {
                Timber.e(e)
                errorMessage = e.message
            } catch (e: CalypsoPoCommandException) {
                Timber.e(e)
                errorMessage = e.message
            } catch (e: CalypsoPoTransactionException) {
                Timber.e(e)
                errorMessage = e.message
            } catch (e: Exception) {
                Timber.e(e)
                errorMessage = e.message
            } finally {

                /*
                 * Step 14 - END: Close the session
                 */
                try {
                    poTransaction.processClosing()

                    if (status == Status.LOADING) {
                        status = Status.ERROR
                    }
                } catch (e: Exception) {
                    Timber.e(e)
                    errorMessage = e.message
                    status = Status.ERROR
                }
            }
        }

        return CardReaderResponse(
            status = status,
            cardType = ticketingSession.poTypeName,
            nbTicketsLeft = nbTicketsLeft,
            contract = "",
            validation = validation,
            errorMessage = errorMessage,
            passValidityEndDate = passValidityEndDate,
            eventDate = eventDate
        )
    }

    companion object {
        const val SINGLE_VALIDATION_AMOUNT = 1
    }
}
