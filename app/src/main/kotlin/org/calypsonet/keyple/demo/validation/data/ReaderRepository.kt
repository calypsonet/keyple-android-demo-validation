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

import android.app.Activity
import android.media.MediaPlayer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.ReaderType
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactlessReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdPlugin
import org.calypsonet.keyple.plugin.bluebird.BluebirdPluginFactoryProvider
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols
import org.calypsonet.keyple.plugin.coppernic.*
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoPlugin
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoPluginFactoryProvider
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoReader
import org.calypsonet.keyple.plugin.famoco.utils.ContactCardCommonProtocols
import org.calypsonet.keyple.plugin.flowbird.FlowbirdPlugin
import org.calypsonet.keyple.plugin.flowbird.FlowbirdPluginFactoryProvider
import org.calypsonet.keyple.plugin.flowbird.FlowbirdUiManager
import org.calypsonet.keyple.plugin.flowbird.contact.FlowbirdContactReader
import org.calypsonet.keyple.plugin.flowbird.contact.SamSlot
import org.calypsonet.keyple.plugin.flowbird.contactless.FlowbirdContactlessReader
import org.calypsonet.keyple.plugin.flowbird.contactless.FlowbirdSupportContactlessProtocols
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcPlugin
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcPluginFactoryProvider
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcReader
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcSupportedProtocols
import org.eclipse.keypop.reader.CardReader
import org.eclipse.keypop.reader.ConfigurableCardReader
import org.eclipse.keypop.reader.ObservableCardReader
import org.eclipse.keypop.reader.spi.CardReaderObservationExceptionHandlerSpi

class ReaderRepository
@Inject
constructor(
    private val readerObservationExceptionHandler: CardReaderObservationExceptionHandlerSpi
) {

  private lateinit var readerType: ReaderType
  // Card
  private lateinit var cardPluginName: String
  private lateinit var cardReaderName: String
  private var cardReaderProtocols = mutableMapOf<String, String>()
  private var cardReader: CardReader? = null
  // SAM
  private lateinit var samPluginName: String
  private lateinit var samReaderNameRegex: String
  private lateinit var samReaderName: String
  private var samReaderProtocolPhysicalName: String? = null
  private var samReaderProtocolLogicalName: String? = null
  private var samReaders: MutableList<CardReader> = mutableListOf()
  // IHM
  private lateinit var successMedia: MediaPlayer
  private lateinit var errorMedia: MediaPlayer

  private fun initReaderType(readerType: ReaderType) {
    when (readerType) {
      ReaderType.BLUEBIRD -> initBluebirdReader()
      ReaderType.COPPERNIC -> initCoppernicReader()
      ReaderType.FAMOCO -> initFamocoReader()
      ReaderType.FLOWBIRD -> initFlowbirdReader()
    }
  }

  private fun initBluebirdReader() {
    readerType = ReaderType.BLUEBIRD
    cardPluginName = BluebirdPlugin.PLUGIN_NAME
    cardReaderName = BluebirdContactlessReader.READER_NAME
    cardReaderProtocols.put(
        BluebirdSupportContactlessProtocols.ISO_14443_4_A.name, CALYPSO_CARD_LOGICAL_PROTOCOL)
    cardReaderProtocols.put(
        BluebirdSupportContactlessProtocols.ISO_14443_4_B.name, CALYPSO_CARD_LOGICAL_PROTOCOL)
    samPluginName = BluebirdPlugin.PLUGIN_NAME
    samReaderNameRegex = ".*ContactReader"
    samReaderName = BluebirdContactReader.READER_NAME
    samReaderProtocolPhysicalName = ContactCardCommonProtocols.ISO_7816_3.name
    samReaderProtocolLogicalName = CALYPSO_SAM_LOGICAL_PROTOCOL
  }

  private fun initCoppernicReader() {
    readerType = ReaderType.COPPERNIC
    cardPluginName = Cone2Plugin.PLUGIN_NAME
    cardReaderName = Cone2ContactlessReader.READER_NAME
    cardReaderProtocols.put(
        ParagonSupportedContactlessProtocols.ISO_14443.name, CALYPSO_CARD_LOGICAL_PROTOCOL)
    samPluginName = Cone2Plugin.PLUGIN_NAME
    samReaderNameRegex = ".*ContactReader_1"
    samReaderName = "${Cone2ContactReader.READER_NAME}_1"
    samReaderProtocolPhysicalName =
        ParagonSupportedContactProtocols.INNOVATRON_HIGH_SPEED_PROTOCOL.name
    samReaderProtocolLogicalName = CALYPSO_SAM_LOGICAL_PROTOCOL
  }

  private fun initFamocoReader() {
    readerType = ReaderType.FAMOCO
    cardPluginName = AndroidNfcPlugin.PLUGIN_NAME
    cardReaderName = AndroidNfcReader.READER_NAME
    cardReaderProtocols.put(
        AndroidNfcSupportedProtocols.ISO_14443_4.name, CALYPSO_CARD_LOGICAL_PROTOCOL)
    samPluginName = AndroidFamocoPlugin.PLUGIN_NAME
    samReaderNameRegex = ".*FamocoReader"
    samReaderName = AndroidFamocoReader.READER_NAME
    samReaderProtocolPhysicalName = ContactCardCommonProtocols.ISO_7816_3.name
    samReaderProtocolLogicalName = CALYPSO_SAM_LOGICAL_PROTOCOL
  }

  private fun initFlowbirdReader() {
    readerType = ReaderType.FLOWBIRD
    cardPluginName = FlowbirdPlugin.PLUGIN_NAME
    cardReaderName = FlowbirdContactlessReader.READER_NAME
    cardReaderProtocols.put(
        FlowbirdSupportContactlessProtocols.ALL.key, CALYPSO_CARD_LOGICAL_PROTOCOL)
    samPluginName = FlowbirdPlugin.PLUGIN_NAME
    samReaderNameRegex = ".*ContactReader_0"
    samReaderName = "${FlowbirdContactReader.READER_NAME}_${(SamSlot.ONE.slotId)}"
    samReaderProtocolPhysicalName = null
    samReaderProtocolLogicalName = null
  }

  @Throws(KeyplePluginException::class)
  fun registerPlugin(activity: Activity, readerType: ReaderType) {
    initReaderType(readerType)
    if (readerType != ReaderType.FLOWBIRD) {
      successMedia = MediaPlayer.create(activity, R.raw.success)
      errorMedia = MediaPlayer.create(activity, R.raw.error)
    }
    runBlocking {
      // Plugin
      val pluginFactory =
          withContext(Dispatchers.IO) {
            when (readerType) {
              ReaderType.BLUEBIRD -> BluebirdPluginFactoryProvider.getFactory(activity)
              ReaderType.COPPERNIC -> Cone2PluginFactoryProvider.getFactory(activity)
              ReaderType.FAMOCO -> AndroidNfcPluginFactoryProvider(activity).getFactory()
              ReaderType.FLOWBIRD -> { // Init files used to sounds and colors from assets
                val mediaFiles: List<String> =
                    listOf("1_default_en.xml", "success.mp3", "error.mp3")
                val situationFiles: List<String> = listOf("1_default_en.xml")
                val translationFiles: List<String> = listOf("0_default.xml")
                FlowbirdPluginFactoryProvider.getFactory(
                    activity = activity,
                    mediaFiles = mediaFiles,
                    situationFiles = situationFiles,
                    translationFiles = translationFiles)
              }
            }
          }
      SmartCardServiceProvider.getService().registerPlugin(pluginFactory)
      // SAM plugin (if different of card plugin)
      if (readerType == ReaderType.FAMOCO) {
        val samPluginFactory =
            withContext(Dispatchers.IO) { AndroidFamocoPluginFactoryProvider.getFactory() }
        SmartCardServiceProvider.getService().registerPlugin(samPluginFactory)
      }
    }
  }

  @Throws(KeyplePluginException::class)
  fun initCardReader(): CardReader? {
    cardReader =
        SmartCardServiceProvider.getService().getPlugin(cardPluginName)?.getReader(cardReaderName)
    cardReader?.let {
      cardReaderProtocols.forEach { entry ->
        (it as ConfigurableCardReader).activateProtocol(entry.key, entry.value)
      }
      (cardReader as ObservableCardReader).setReaderObservationExceptionHandler(
          readerObservationExceptionHandler)
    }
    return cardReader
  }

  fun getCardReader(): CardReader? {
    return cardReader
  }

  fun getCardReaderProtocolLogicalName(): String {
    return CALYPSO_CARD_LOGICAL_PROTOCOL
  }

  @Throws(KeyplePluginException::class)
  fun initSamReaders(): List<CardReader> {
    if (readerType == ReaderType.FAMOCO) {
      samReaders =
          SmartCardServiceProvider.getService()
              .getPlugin(samPluginName)
              ?.readers
              ?.filter { it.name == samReaderName }
              ?.toMutableList()
              ?: mutableListOf()
    } else {
      samReaders =
          SmartCardServiceProvider.getService()
              .getPlugin(samPluginName)
              ?.readers
              ?.filter { !it.isContactless }
              ?.toMutableList()
              ?: mutableListOf()
    }
    samReaders.forEach {
      if (it is ConfigurableCardReader) {
        it.activateProtocol(samReaderProtocolPhysicalName, samReaderProtocolLogicalName)
      }
    }
    return samReaders
  }

  fun getSamPlugin(): Plugin = SmartCardServiceProvider.getService().getPlugin(samPluginName)

  fun getSamReader(): CardReader? {
    return if (samReaders.isNotEmpty()) {
      val filteredByName = samReaders.filter { it.name == samReaderName }
      return if (filteredByName.isEmpty()) {
        samReaders.first()
      } else {
        filteredByName.first()
      }
    } else {
      null
    }
  }

  fun clear() {
    cardReaderProtocols.forEach { entry ->
      (cardReader as ConfigurableCardReader).deactivateProtocol(entry.key)
    }
    samReaders.forEach {
      if (it is ConfigurableCardReader) {
        it.deactivateProtocol(samReaderProtocolPhysicalName)
      }
    }
    if (readerType != ReaderType.FLOWBIRD) {
      successMedia.stop()
      successMedia.release()
      errorMedia.stop()
      errorMedia.release()
    }
  }

  fun displayResultSuccess(): Boolean {
    if (readerType == ReaderType.FLOWBIRD) {
      FlowbirdUiManager.displayResultSuccess()
    } else {
      successMedia.start()
    }
    return true
  }

  fun displayResultFailed(): Boolean {
    if (readerType == ReaderType.FLOWBIRD) {
      FlowbirdUiManager.displayResultFailed()
    } else {
      errorMedia.start()
    }
    return true
  }

  companion object {
    private const val CALYPSO_CARD_LOGICAL_PROTOCOL = "CalypsoCardProtocol"
    private const val CALYPSO_SAM_LOGICAL_PROTOCOL = "CalypsoSamProtocol"
  }
}
