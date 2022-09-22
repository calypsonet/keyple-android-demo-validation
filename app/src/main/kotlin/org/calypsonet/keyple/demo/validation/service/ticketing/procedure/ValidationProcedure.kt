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
package org.calypsonet.keyple.demo.validation.service.ticketing.procedure

import android.content.Context
import java.util.Calendar
import java.util.Date
import org.calypsonet.keyple.demo.common.constant.CardConstant
import org.calypsonet.keyple.demo.common.model.EventStructure
import org.calypsonet.keyple.demo.common.model.type.DateCompact
import org.calypsonet.keyple.demo.common.model.type.PriorityCode
import org.calypsonet.keyple.demo.common.model.type.TimeCompact
import org.calypsonet.keyple.demo.common.model.type.VersionNumber
import org.calypsonet.keyple.demo.common.parser.ContractStructureParser
import org.calypsonet.keyple.demo.common.parser.EnvironmentHolderStructureParser
import org.calypsonet.keyple.demo.common.parser.EventStructureParser
import org.calypsonet.keyple.demo.validation.ApplicationSettings
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.ContractVersionNumberErrorException
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.EnvironmentException
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.EnvironmentExceptionKey
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.EventException
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.EventExceptionKey
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.NoContractAvailableException
import org.calypsonet.keyple.demo.validation.service.ticketing.exception.ValidationException
import org.calypsonet.keyple.demo.validation.service.ticketing.model.CardReaderResponse
import org.calypsonet.keyple.demo.validation.service.ticketing.model.Location
import org.calypsonet.keyple.demo.validation.service.ticketing.model.Status
import org.calypsonet.keyple.demo.validation.service.ticketing.model.Validation
import org.calypsonet.keyple.demo.validation.service.ticketing.model.mapper.ValidationMapper
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.calypso.transaction.CardSecuritySetting
import org.calypsonet.terminal.calypso.transaction.CardTransactionManager
import org.calypsonet.terminal.reader.CardReader
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.joda.time.DateTime
import timber.log.Timber

class ValidationProcedure {

  fun launch(
      now: DateTime,
      context: Context,
      validationAmount: Int,
      cardReader: CardReader,
      calypsoCard: CalypsoCard,
      cardSecuritySettings: CardSecuritySetting,
      locations: List<Location>
  ): CardReaderResponse {

    var status: Status = Status.LOADING
    var errorMessage: String? = null
    val cardTransaction: CardTransactionManager?
    var eventDate: Date? = null
    var passValidityEndDate: Date? = null
    var nbTicketsLeft: Int? = null
    var validation: Validation? = null

    val calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

    // Create a card transaction for validation.
    cardTransaction =
        try {
          calypsoCardExtensionProvider.createCardTransaction(
              cardReader, calypsoCard, cardSecuritySettings)
        } catch (e: Exception) {
          Timber.w(e)
          status = Status.ERROR
          errorMessage = e.message
          null
        }

    if (cardTransaction != null) {
      try {

        // ***************** Event and Environment Analysis
        // Step 1 - Open a Validation session reading the environment record.
        cardTransaction.prepareReadRecord(CardConstant.SFI_ENVIRONMENT_AND_HOLDER, 1)
        cardTransaction.processOpening(WriteAccessLevel.DEBIT)

        // Step 2 - Unpack environment structure from the binary present in the environment record.
        val efEnvironmentHolder = calypsoCard.getFileBySfi(CardConstant.SFI_ENVIRONMENT_AND_HOLDER)
        val environmentContent = efEnvironmentHolder.data.content
        val environment = EnvironmentHolderStructureParser().parse(environmentContent)

        // Step 3 - If EnvVersionNumber of the Environment structure is not the expected one (==1
        // for the current version) reject the card. <Abort Secure Session>
        if (environment.envVersionNumber != VersionNumber.CURRENT_VERSION) {
          status = Status.INVALID_CARD
          throw EnvironmentException(EnvironmentExceptionKey.WRONG_VERSION_NUMBER)
        }

        // Step 4 - If EnvEndDate points to a date in the past reject the card. <Abort Secure
        // Session>
        val envEndDate = DateTime(environment.envEndDate.date)
        if (envEndDate.isBefore(now)) {
          status = Status.INVALID_CARD
          throw EnvironmentException(EnvironmentExceptionKey.EXPIRED)
        }

        // Step 5 - Read and unpack the last event record.
        cardTransaction.prepareReadRecord(CardConstant.SFI_EVENTS_LOG, 1)
        cardTransaction.processCommands()

        val efEventLog = calypsoCard.getFileBySfi(CardConstant.SFI_EVENTS_LOG)
        val eventContent = efEventLog.data.content
        val event = EventStructureParser().parse(eventContent)

        // Step 6 - If EventVersionNumber is not the expected one (==1 for the current version)
        // reject the card. <Abort Secure Session>
        val eventVersionNumber = event.eventVersionNumber

        if (eventVersionNumber != VersionNumber.CURRENT_VERSION) {
          if (eventVersionNumber == VersionNumber.UNDEFINED) {
            status = Status.EMPTY_CARD
            throw EventException(EventExceptionKey.CLEAN_CARD)
          } else {
            status = Status.INVALID_CARD
            throw EventException(EventExceptionKey.WRONG_VERSION_NUMBER)
          }
        }

        // Step 7 - Store the PriorityCode fields in a persistent object.
        val contractPriorities = mutableListOf<Pair<Int, PriorityCode>>()

        // ***************** Best Contract Search
        // Step 7 - Create a list of PriorityCode fields that are different from 0 or 31.
        if (event.contractPriority1 != PriorityCode.FORBIDDEN &&
            event.contractPriority1 != PriorityCode.EXPIRED) {
          contractPriorities.add(Pair(1, event.contractPriority1))
        }
        if (event.contractPriority2 != PriorityCode.FORBIDDEN &&
            event.contractPriority2 != PriorityCode.EXPIRED) {
          contractPriorities.add(Pair(2, event.contractPriority2))
        }
        if (event.contractPriority3 != PriorityCode.FORBIDDEN &&
            event.contractPriority3 != PriorityCode.EXPIRED) {
          contractPriorities.add(Pair(3, event.contractPriority3))
        }
        if (event.contractPriority4 != PriorityCode.FORBIDDEN &&
            event.contractPriority4 != PriorityCode.EXPIRED) {
          contractPriorities.add(Pair(4, event.contractPriority4))
        }

        if (contractPriorities.isEmpty()) {
          // Step 9 - If the list is empty go to END.
          status = Status.EMPTY_CARD
          throw NoContractAvailableException()
        }

        var priority1 = event.contractPriority1
        var priority2 = event.contractPriority2
        var priority3 = event.contractPriority3
        var priority4 = event.contractPriority4
        var contractUsed = 0
        var writeEvent = false

        // Step 10 - For each element in the list:
        val sortedPriorities = contractPriorities.toList().sortedBy { it.second.key }

        // Step 11 - For each element in the list:
        for (it in sortedPriorities) {
          val record = it.first
          val contractPriority = it.second

          // Step 11.1 - Read and unpack the contract record for the index being iterated.
          cardTransaction.prepareReadRecord(CardConstant.SFI_CONTRACTS, record)

          cardTransaction.processCommands()

          val efContractParser = calypsoCard.getFileBySfi(CardConstant.SFI_CONTRACTS)
          val contractContent = efContractParser.data.allRecordsContent[record]!!
          val contract = ContractStructureParser().parse(contractContent)

          // Step 11.2 - If ContractVersionNumber is not the expected one (==1 for the current
          // version) reject the card. <Abort Secure Session>
          if (contract.contractVersionNumber != VersionNumber.CURRENT_VERSION) {
            status = Status.INVALID_CARD
            throw ContractVersionNumberErrorException()
          }

          // Step 11.3 - '  If ContractAuthenticator is not 0 perform the verification of the value
          // by using the PSO Verify Signature command of the SAM.
          @Suppress("ControlFlowWithEmptyBody")
          if (contract.contractAuthenticator != 0) {
            // Step 11.3.1 - If the value is wrong reject the card. <Abort Secure Session>
            // Step 11.3.2 - If the value of ContractSaleSam is present in the SAM Black List reject
            // the card. <Abort Secure Session>
            // TODO: steps 11.3.1 & 11.3.2
          }

          // Step 11.4 - If ContractValidityEndDate points to a date in the past update the
          // associated ContractPriorty field present in the persistent object to 31 and move to the
          // next element in the list
          val contractValidityEndDate = DateTime(contract.contractValidityEndDate.date)
          if (contractValidityEndDate.isBefore(now)) {
            when (record) {
              1 -> priority1 = PriorityCode.EXPIRED
              2 -> priority2 = PriorityCode.EXPIRED
              3 -> priority3 = PriorityCode.EXPIRED
              4 -> priority4 = PriorityCode.EXPIRED
            }
            status = Status.EMPTY_CARD
            errorMessage = context.getString(R.string.expired_title)
            writeEvent = true
            continue
          }

          // Step 11.5 - If the ContractTariff value for the contract read is 2 or 3:
          if (contractPriority == PriorityCode.MULTI_TRIP ||
              contractPriority == PriorityCode.STORED_VALUE) {

            // Step 11.5.1 - Read and unpack the counter associated to the contract (1st counter for
            // Contract #1 and so forth).
            cardTransaction.prepareReadCounter(CardConstant.SFI_COUNTER, COUNTER_RECORDS_NB)
            cardTransaction.processCommands()

            val efCounter = calypsoCard.getFileBySfi(CardConstant.SFI_COUNTER)
            val counterValue = efCounter.data.getContentAsCounterValue(record)

            // Step 11.5.2 - If the counter value is 0 update the associated ContractPriorty field
            // present in the persistent object to 31 and move to the next element in the list
            if (counterValue == 0) {
              when (record) {
                1 -> priority1 = PriorityCode.EXPIRED
                2 -> priority2 = PriorityCode.EXPIRED
                3 -> priority3 = PriorityCode.EXPIRED
                4 -> priority4 = PriorityCode.EXPIRED
              }
              status = Status.EMPTY_CARD
              errorMessage = context.getString(R.string.no_trips_left)
              writeEvent = true
              continue
            }
            // Step 11.5.3 - If the counter value is > 0 && ContractTariff == 3 && CounterValue <
            // ValidationAmount move to the next element in the list
            else if (counterValue > 0 &&
                contractPriority == PriorityCode.STORED_VALUE &&
                counterValue < validationAmount) {
              status = Status.EMPTY_CARD
              errorMessage = context.getString(R.string.no_trips_left)
              continue
            }
            // Step 11.5.4 - UPDATE COUNTER Decrement the counter value by the appropriate amount (1
            // if ContractTariff is 2, and the configured value for the trip if ContractTariff is
            // 3).
            else {
              val decrement =
                  when (contractPriority) {
                    PriorityCode.MULTI_TRIP -> SINGLE_VALIDATION_AMOUNT
                    PriorityCode.STORED_VALUE -> validationAmount
                    else -> 0
                  }
              if (decrement > 0) {
                cardTransaction.prepareDecreaseCounter(CardConstant.SFI_COUNTER, record, decrement)

                cardTransaction.processCommands()
                nbTicketsLeft = counterValue - decrement
              }
            }
          } else if (contractPriority == PriorityCode.SEASON_PASS) {
            passValidityEndDate = contract.contractValidityEndDate.date
          }

          // We will create a new event for this contract
          contractUsed = record
          writeEvent = true
          break
        }

        if (writeEvent) {

          val eventToWrite: EventStructure
          if (contractUsed > 0) {
            // Create a new validation event
            val calendar = Calendar.getInstance()
            calendar.time = now.toDate()
            calendar.set(Calendar.MILLISECOND, 0)
            eventDate = calendar.time
            eventToWrite =
                EventStructure(
                    eventVersionNumber = VersionNumber.CURRENT_VERSION,
                    eventDateStamp = DateCompact(eventDate),
                    eventTimeStamp = TimeCompact(eventDate),
                    eventLocation = ApplicationSettings.location.id,
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
            // Update old event's priorities
            eventToWrite =
                EventStructure(
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

          // Step 13 - Pack the Event structure and append it to the event file
          val eventBytesToWrite = EventStructureParser().generate(eventToWrite)
          cardTransaction.prepareUpdateRecord(CardConstant.SFI_EVENTS_LOG, 1, eventBytesToWrite)
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

        // Step 14 - END: Close the session
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
