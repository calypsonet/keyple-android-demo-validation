/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.demo.validation.ticketing.procedure

import android.content.Context
import java.util.Calendar
import java.util.Date
import org.calypsonet.keyple.demo.common.parser.CardContractParser
import org.calypsonet.keyple.demo.common.parser.CardEnvironmentHolderParser
import org.calypsonet.keyple.demo.common.parser.CardEventParser
import org.calypsonet.keyple.demo.common.parser.model.CardEvent
import org.calypsonet.keyple.demo.common.parser.model.constant.ContractPriority
import org.calypsonet.keyple.demo.common.parser.model.constant.VersionNumber
import org.calypsonet.keyple.demo.common.parser.util.DateUtil
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.exception.ContractVersionNumberErrorException
import org.calypsonet.keyple.demo.validation.exception.EnvironmentException
import org.calypsonet.keyple.demo.validation.exception.EnvironmentExceptionKey
import org.calypsonet.keyple.demo.validation.exception.EventException
import org.calypsonet.keyple.demo.validation.exception.EventExceptionKey
import org.calypsonet.keyple.demo.validation.exception.NoContractAvailableException
import org.calypsonet.keyple.demo.validation.exception.NoLocationDefinedException
import org.calypsonet.keyple.demo.validation.exception.NoSamForValidationException
import org.calypsonet.keyple.demo.validation.exception.ValidationException
import org.calypsonet.keyple.demo.validation.models.CardReaderResponse
import org.calypsonet.keyple.demo.validation.models.Location
import org.calypsonet.keyple.demo.validation.models.Status
import org.calypsonet.keyple.demo.validation.models.Validation
import org.calypsonet.keyple.demo.validation.models.ValidationAppSettings
import org.calypsonet.keyple.demo.validation.models.mapper.ValidationMapper
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.RECORD_NUMBER_1
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.RECORD_NUMBER_2
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.RECORD_NUMBER_3
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.RECORD_NUMBER_4
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.SAM_PROFILE_NAME
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.SFI_Contracts
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.SFI_Counter
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.SFI_EnvironmentAndHolder
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo.SFI_EventsLog
import org.calypsonet.keyple.demo.validation.ticketing.ITicketingSession
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.calypso.transaction.CardTransactionManager
import org.calypsonet.terminal.reader.CardReader
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.joda.time.DateTime
import timber.log.Timber

/** @author youssefamrani */
class ValidationProcedure {

  fun launch(
      now: DateTime,
      context: Context,
      validationAmount: Int,
      locations: List<Location>,
      calypsoCard: CalypsoCard,
      samReader: CardReader?,
      samPluginName: String?,
      ticketingSession: ITicketingSession
  ): CardReaderResponse {

    val card = ticketingSession.cardReader

    var status: Status = Status.LOADING
    var errorMessage: String? = null
    val cardTransaction: CardTransactionManager?
    var eventDate: Date? = null
    var passValidityEndDate: Date? = null
    var nbTicketsLeft: Int? = null
    var validation: Validation? = null

    val calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

    /*
     * Step 1 - Open a Validation session reading the environment record.
     */
    cardTransaction =
        try {
          if (samReader != null) {
            ticketingSession.setupCardResourceService(SAM_PROFILE_NAME, samPluginName)

            calypsoCardExtensionProvider.createCardTransaction(
                card, calypsoCard, ticketingSession.getSecuritySettings())
          } else {
            throw NoSamForValidationException()
          }
        } catch (e: Exception) {
          Timber.w(e)
          status = Status.ERROR
          errorMessage = e.message
          null
        }

    if (cardTransaction != null) {
      try {

        /** ***************** Event and Environment Analysis */
        /*
         * Step 2 - Unpack environment structure from the binary present in the environment record.
         */
        cardTransaction.prepareReadRecord(SFI_EnvironmentAndHolder, RECORD_NUMBER_1.toInt())

        /*
         * Open a transaction to read/write the Calypso PO
         */
        cardTransaction.processOpening(WriteAccessLevel.DEBIT)

        val efEnvironmentHolder = calypsoCard.getFileBySfi(SFI_EnvironmentAndHolder)
        val environmentContent = efEnvironmentHolder.data.content
        val environment = CardEnvironmentHolderParser().parse(environmentContent)

        /*
         * Step 3 - If EnvVersionNumber of the Environment structure is not the expected one (==1 for the current version) reject the card.
         * <Abort Transaction if inTransactionFlag is true and exit process>
         */
        if (environment.envVersionNumber != VersionNumber.CURRENT_VERSION.key) {
          status = Status.INVALID_CARD
          throw EnvironmentException(EnvironmentExceptionKey.WRONG_VERSION_NUMBER)
        }

        /*
         * Step 4 - If EnvEndDate points to a date in the past reject the card.
         * <Abort Transaction if inTransactionFlag is true and exit process>
         */
        val envEndDate = DateTime(environment.getEnvEndDateAsDate())
        if (envEndDate.isBefore(now)) {
          status = Status.INVALID_CARD
          throw EnvironmentException(EnvironmentExceptionKey.EXPIRED)
        }

        /*
         * Step 5 - Read and unpack the last event record.
         */
        cardTransaction.prepareReadRecord(SFI_EventsLog, RECORD_NUMBER_1.toInt())
        cardTransaction.processCommands()

        val efEventLog = calypsoCard.getFileBySfi(SFI_EventsLog)
        val eventContent = efEventLog.data.content
        val event = CardEventParser().parse(eventContent)

        /*
         * Step 6 - If EventVersionNumber is not the expected one (==1 for the current version) reject the card.
         * <Abort Transaction and exit process>
         */
        val eventVersionNumber = event.eventVersionNumber

        if (eventVersionNumber != VersionNumber.CURRENT_VERSION.key) {
          if (eventVersionNumber == VersionNumber.UNDEFINED.key) {
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
        val contractPriorities = mutableListOf<Pair<Int, ContractPriority>>()

        /** ***************** Best Contract Search */
        /*
         * Step 7 - Create a list of ContractPriority fields that are different from 0 or 31.
         */
        if (event.contractPriority1 != ContractPriority.FORBIDDEN &&
            event.contractPriority1 != ContractPriority.EXPIRED) {
          contractPriorities.add(Pair(1, event.contractPriority1))
        }
        if (event.contractPriority2 != ContractPriority.FORBIDDEN &&
            event.contractPriority2 != ContractPriority.EXPIRED) {
          contractPriorities.add(Pair(2, event.contractPriority2))
        }
        if (event.contractPriority3 != ContractPriority.FORBIDDEN &&
            event.contractPriority3 != ContractPriority.EXPIRED) {
          contractPriorities.add(Pair(3, event.contractPriority3))
        }
        if (event.contractPriority4 != ContractPriority.FORBIDDEN &&
            event.contractPriority4 != ContractPriority.EXPIRED) {
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
        val sortedPriorities = contractPriorities.toList().sortedBy { it.second.key }

        /*
         * Step 11 - For each element in the list:
         */
        for (it in sortedPriorities) {
          val record = it.first
          val contractPriority = it.second

          /*
           * Step 11.1 - Read and unpack the contract record for the index being iterated.
           */
          cardTransaction.prepareReadRecord(SFI_Contracts, record)

          cardTransaction.processCommands()

          val efContractParser = calypsoCard.getFileBySfi(SFI_Contracts)
          val contractContent = efContractParser.data.allRecordsContent[record]!!
          val contract = CardContractParser().parse(contractContent)

          /*
           * Step 11.2 - If ContractVersionNumber is not the expected one (==1 for the current version) reject the card.
           * <Abort Transaction and exit process>
           */
          if (contract.contractVersionNumber != VersionNumber.CURRENT_VERSION) {
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
            // TODO: steps 11.3.1 & 11.3.2
          }

          /*
           * Step 11.4 - If ContractValidityEndDate points to a date in the past update the associated ContractPriorty field
           * present in the persistent object to 31 and move to the next element in the list
           */
          val contractValidityEndDate = DateTime(contract.getContractValidityEndDateAsDate())
          if (contractValidityEndDate.isBefore(now)) {
            when (record) {
              RECORD_NUMBER_1.toInt() -> priority1 = ContractPriority.EXPIRED
              RECORD_NUMBER_2.toInt() -> priority2 = ContractPriority.EXPIRED
              RECORD_NUMBER_3.toInt() -> priority3 = ContractPriority.EXPIRED
              RECORD_NUMBER_4.toInt() -> priority4 = ContractPriority.EXPIRED
            }
            status = Status.EMPTY_CARD
            errorMessage = context.getString(R.string.expired_title)
            writeEvent = true
            continue
          }

          /*
           * Step 11.5 - If the ContractTariff value for the contract read is 2 or 3:
           */
          if (contractPriority == ContractPriority.MULTI_TRIP ||
              contractPriority == ContractPriority.STORED_VALUE) {
            /*
             * Step 11.5.1 - Read and unpack the counter associated to the contract (1st counter for Contract #1 and so forth).
             */

            cardTransaction.prepareReadCounter(SFI_Counter, COUNTER_RECORDS_NB)
            cardTransaction.processCommands()

            val efCounter = calypsoCard.getFileBySfi(SFI_Counter)
            val counterValue = efCounter.data.getContentAsCounterValue(record)

            /*
             * Step 11.5.2 - If the counter value is 0 update the associated ContractPriorty field
             * present in the persistent object to 31 and move to the next element in the list
             */
            if (counterValue == 0) {
              when (record) {
                RECORD_NUMBER_1.toInt() -> priority1 = ContractPriority.EXPIRED
                RECORD_NUMBER_2.toInt() -> priority2 = ContractPriority.EXPIRED
                RECORD_NUMBER_3.toInt() -> priority3 = ContractPriority.EXPIRED
                RECORD_NUMBER_4.toInt() -> priority4 = ContractPriority.EXPIRED
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
                contractPriority == ContractPriority.STORED_VALUE &&
                counterValue < validationAmount) {
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
              val decrement =
                  when (contractPriority) {
                    ContractPriority.MULTI_TRIP -> SINGLE_VALIDATION_AMOUNT
                    ContractPriority.STORED_VALUE -> validationAmount
                    else -> 0
                  }
              if (decrement > 0) {
                cardTransaction.prepareDecreaseCounter(SFI_Counter, record, decrement)

                cardTransaction.processCommands()
                nbTicketsLeft = counterValue - decrement
              }
            }
          } else if (contractPriority == ContractPriority.SEASON_PASS) {
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
          if (ValidationAppSettings.location == null) {
            throw NoLocationDefinedException()
          }

          val eventToWrite: CardEvent
          if (contractUsed > 0) {
            /*
             * Create a new validation event
             */

            val calendar = Calendar.getInstance()
            calendar.time = now.toDate()
            calendar.set(Calendar.MILLISECOND, 0)
            eventDate = calendar.time
            eventToWrite =
                CardEvent(
                    eventVersionNumber = VersionNumber.CURRENT_VERSION.key,
                    eventDateStamp = DateUtil.dateToDateCompact(eventDate),
                    eventTimeStamp = DateUtil.dateToTimeCompact(eventDate),
                    eventLocation = ValidationAppSettings.location!!.id,
                    eventContractUsed = contractUsed,
                    contractPriority1 = priority1,
                    contractPriority2 = priority2,
                    contractPriority3 = priority3,
                    contractPriority4 = priority4)
            validation = ValidationMapper.map(eventToWrite, locations)

            Timber.i("Validation procedure result : SUCCESS")
            status = Status.SUCCESS
            errorMessage = null
          } else {
            /*
             * Update old event's priorities
             */
            eventToWrite =
                CardEvent(
                    eventVersionNumber = event.eventVersionNumber,
                    eventDateStamp = event.eventDateStamp,
                    eventTimeStamp = event.eventTimeStamp,
                    eventLocation = event.eventLocation,
                    eventContractUsed = event.eventContractUsed,
                    contractPriority1 = priority1,
                    contractPriority2 = priority2,
                    contractPriority3 = priority3,
                    contractPriority4 = priority4)
          }

          /*
           * Step 13 - Pack the Event structure and append it to the event file
           */
          val eventBytesToWrite = CardEventParser().generate(eventToWrite)
          cardTransaction.prepareUpdateRecord(
              SFI_EventsLog, RECORD_NUMBER_1.toInt(), eventBytesToWrite)
          cardTransaction.processCommands()
        } else {
          Timber.i("Validation procedure result : Failed - No valid contract found")
          if (errorMessage.isNullOrEmpty()) {
            errorMessage = context.getString(R.string.no_valid_title_detected)
          }
        }
      } catch (e: ValidationException) {
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
          if (status == Status.SUCCESS) {
            cardTransaction.processClosing()
          } else {
            cardTransaction.processCancel()
          }

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
        nbTicketsLeft = nbTicketsLeft,
        contract = "",
        validation = validation,
        errorMessage = errorMessage,
        passValidityEndDate = passValidityEndDate,
        eventDate = eventDate)
  }

  companion object {
    const val COUNTER_RECORDS_NB = 4
    const val SINGLE_VALIDATION_AMOUNT = 1
  }
}
