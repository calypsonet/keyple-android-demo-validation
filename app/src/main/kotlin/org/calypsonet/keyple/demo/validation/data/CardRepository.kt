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
package org.calypsonet.keyple.demo.validation.data

import android.content.Context
import java.time.LocalDate
import java.time.LocalDateTime
import org.calypsonet.keyple.demo.common.constant.CardConstant
import org.calypsonet.keyple.demo.common.model.EventStructure
import org.calypsonet.keyple.demo.common.model.type.DateCompact
import org.calypsonet.keyple.demo.common.model.type.PriorityCode
import org.calypsonet.keyple.demo.common.model.type.TimeCompact
import org.calypsonet.keyple.demo.common.model.type.VersionNumber
import org.calypsonet.keyple.demo.common.parser.ContractStructureParser
import org.calypsonet.keyple.demo.common.parser.EnvironmentHolderStructureParser
import org.calypsonet.keyple.demo.common.parser.EventStructureParser
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.AppSettings
import org.calypsonet.keyple.demo.validation.data.model.CardReaderResponse
import org.calypsonet.keyple.demo.validation.data.model.Location
import org.calypsonet.keyple.demo.validation.data.model.Status
import org.calypsonet.keyple.demo.validation.data.model.Validation
import org.calypsonet.keyple.demo.validation.data.model.mapper.ValidationMapper
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keypop.calypso.card.WriteAccessLevel
import org.eclipse.keypop.calypso.card.card.CalypsoCard
import org.eclipse.keypop.calypso.card.transaction.ChannelControl
import org.eclipse.keypop.calypso.card.transaction.SecureRegularModeTransactionManager
import org.eclipse.keypop.calypso.card.transaction.SymmetricCryptoSecuritySetting
import org.eclipse.keypop.reader.CardReader
import timber.log.Timber

class CardRepository {

  fun executeValidationProcedure(
      validationDateTime: LocalDateTime,
      context: Context,
      validationAmount: Int,
      cardReader: CardReader,
      calypsoCard: CalypsoCard,
      cardSecuritySettings: SymmetricCryptoSecuritySetting,
      locations: List<Location>
  ): CardReaderResponse {

    var status: Status = Status.LOADING
    var errorMessage: String? = null
    val cardTransaction: SecureRegularModeTransactionManager?
    var passValidityEndDate: LocalDate? = null
    var nbTicketsLeft: Int? = null
    var validation: Validation? = null

    val calypsoCardApiFactory = CalypsoExtensionService.getInstance().calypsoCardApiFactory

    // Create a card transaction for validation.
    cardTransaction =
        try {
          calypsoCardApiFactory.createSecureRegularModeTransactionManager(
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
        cardTransaction
            .prepareOpenSecureSession(WriteAccessLevel.DEBIT)
            .prepareReadRecord(CardConstant.SFI_ENVIRONMENT_AND_HOLDER, 1)
            .processCommands(ChannelControl.KEEP_OPEN)

        // Step 2 - Unpack environment structure from the binary present in the environment record.
        val efEnvironmentHolder = calypsoCard.getFileBySfi(CardConstant.SFI_ENVIRONMENT_AND_HOLDER)
        val environmentContent = efEnvironmentHolder.data.content
        val environment = EnvironmentHolderStructureParser().parse(environmentContent)

        // Step 3 - If EnvVersionNumber of the Environment structure is not the expected one (==1
        // for the current version) reject the card. <Abort Secure Session>
        if (environment.envVersionNumber != VersionNumber.CURRENT_VERSION) {
          status = Status.INVALID_CARD
          throw RuntimeException("Environment error: wrong version number")
        }

        // Step 4 - If EnvEndDate points to a date in the past reject the card. <Abort Secure
        // Session>
        if (environment.envEndDate.getDate().isBefore(validationDateTime.toLocalDate())) {
          status = Status.INVALID_CARD
          throw RuntimeException("Environment error: end date expired")
        }

        // Step 5 - Read and unpack the last event record.
        cardTransaction
            .prepareReadRecord(CardConstant.SFI_EVENTS_LOG, 1)
            .processCommands(ChannelControl.KEEP_OPEN)

        val efEventLog = calypsoCard.getFileBySfi(CardConstant.SFI_EVENTS_LOG)
        val eventContent = efEventLog.data.content
        val event = EventStructureParser().parse(eventContent)

        // Step 6 - If EventVersionNumber is not the expected one (==1 for the current version)
        // reject the card. <Abort Secure Session>
        val eventVersionNumber = event.eventVersionNumber

        if (eventVersionNumber != VersionNumber.CURRENT_VERSION) {
          if (eventVersionNumber == VersionNumber.UNDEFINED) {
            status = Status.EMPTY_CARD
            throw RuntimeException("No valid title detected")
          } else {
            status = Status.INVALID_CARD
            throw RuntimeException("Event error: wrong version number")
          }
        }

        // Step 7 - Store the PriorityCode fields in a persistent object.
        val contractPriorities = mutableListOf<Pair<Int, PriorityCode>>()

        // ***************** Best Contract Search
        // Step 7 - Create a list of PriorityCode fields that are different from FORBIDDEN and
        // EXPIRED.
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
          throw RuntimeException("No valid title detected")
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
          cardTransaction
              .prepareReadRecord(CardConstant.SFI_CONTRACTS, record)
              .processCommands(ChannelControl.KEEP_OPEN)

          val efContractParser = calypsoCard.getFileBySfi(CardConstant.SFI_CONTRACTS)
          val contractContent = efContractParser.data.allRecordsContent[record]!!
          val contract = ContractStructureParser().parse(contractContent)

          // Step 11.2 - If ContractVersionNumber is not the expected one (==1 for the current
          // version) reject the card. <Abort Secure Session>
          if (contract.contractVersionNumber != VersionNumber.CURRENT_VERSION) {
            status = Status.INVALID_CARD
            throw RuntimeException("Contract Version Number error (!= CURRENT_VERSION)")
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
          if (contract.contractValidityEndDate
              .getDate()
              .isBefore(validationDateTime.toLocalDate())) {
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

            val nbContractRecords =
                when (calypsoCard.productType) {
                  CalypsoCard.ProductType.BASIC -> 1
                  CalypsoCard.ProductType.LIGHT -> 2
                  else -> 4
                }

            // Step 11.5.1 - Read and unpack the counter associated to the contract (1st counter for
            // Contract #1 and so forth).
            cardTransaction
                .prepareReadCounter(CardConstant.SFI_COUNTERS, nbContractRecords)
                .processCommands(ChannelControl.KEEP_OPEN)

            val efCounter = calypsoCard.getFileBySfi(CardConstant.SFI_COUNTERS)
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
                cardTransaction
                    .prepareDecreaseCounter(CardConstant.SFI_COUNTERS, record, decrement)
                    .processCommands(ChannelControl.KEEP_OPEN)
                nbTicketsLeft = counterValue - decrement
              }
            }
          } else if (contractPriority == PriorityCode.SEASON_PASS) {
            passValidityEndDate = contract.contractValidityEndDate.getDate()
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
            eventToWrite =
                EventStructure(
                    eventVersionNumber = VersionNumber.CURRENT_VERSION,
                    eventDateStamp = DateCompact(validationDateTime.toLocalDate()),
                    eventTimeStamp = TimeCompact(validationDateTime),
                    eventLocation = AppSettings.location.id,
                    eventContractUsed = contractUsed,
                    contractPriority1 = priority1,
                    contractPriority2 = priority2,
                    contractPriority3 = priority3,
                    contractPriority4 = priority4)
            validation = ValidationMapper.map(eventToWrite, locations)

            Timber.i("Validation procedure result: SUCCESS")
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
          cardTransaction
              .prepareUpdateRecord(CardConstant.SFI_EVENTS_LOG, 1, eventBytesToWrite)
              .processCommands(ChannelControl.KEEP_OPEN)
        } else {
          Timber.i("Validation procedure result: Failed - No valid contract found")
          if (errorMessage.isNullOrEmpty()) {
            errorMessage = context.getString(R.string.no_valid_title_detected)
          }
        }
      } catch (e: Exception) {
        Timber.e(e)
        errorMessage = e.message
      } finally {
        // Step 14 - END: Close the session
        try {
          if (status == Status.SUCCESS) {
            cardTransaction.prepareCloseSecureSession().processCommands(ChannelControl.CLOSE_AFTER)
          } else {
            cardTransaction.prepareCancelSecureSession().processCommands(ChannelControl.CLOSE_AFTER)
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
        eventDateTime = validationDateTime)
  }

  companion object {
    const val SINGLE_VALIDATION_AMOUNT = 1
  }
}
