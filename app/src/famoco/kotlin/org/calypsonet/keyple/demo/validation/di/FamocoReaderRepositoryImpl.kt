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
package org.calypsonet.keyple.demo.validation.di

import android.app.Activity
import android.media.MediaPlayer
import javax.inject.Inject
import org.calypsonet.keyple.demo.validation.CardReader.CardReaderProtocol
import org.calypsonet.keyple.demo.validation.CardReader.IReaderRepository
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoPlugin
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoPluginFactoryProvider
import org.calypsonet.keyple.plugin.famoco.AndroidFamocoReader
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.ConfigurableCardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.spi.CardReaderObservationExceptionHandlerSpi
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.resource.spi.ReaderConfiguratorSpi
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcPlugin
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcPluginFactoryProvider
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcReader
import org.eclipse.keyple.plugin.android.nfc.AndroidNfcSupportedProtocols
import timber.log.Timber

class FamocoReaderRepositoryImpl
@Inject
constructor(
    private val readerObservationExceptionHandler: CardReaderObservationExceptionHandlerSpi
) : IReaderRepository {

  lateinit var successMedia: MediaPlayer
  lateinit var errorMedia: MediaPlayer

  override var cardReader: CardReader? = null
  override var samReaders: MutableList<CardReader> = mutableListOf()

  @Throws(KeyplePluginException::class)
  override fun registerPlugin(activity: Activity) {

    successMedia = MediaPlayer.create(activity, R.raw.success)
    errorMedia = MediaPlayer.create(activity, R.raw.error)

    try {
      val androidNfcPluginFactory = AndroidNfcPluginFactoryProvider(activity).getFactory()
      SmartCardServiceProvider.getService().registerPlugin(androidNfcPluginFactory)

      val androidFamocoPluginFactory = AndroidFamocoPluginFactoryProvider.getFactory()
      SmartCardServiceProvider.getService().registerPlugin(androidFamocoPluginFactory)
    } catch (e: UnsatisfiedLinkError) {
      Timber.w(e)
    }
  }

  override fun getPlugin(): Plugin =
      SmartCardServiceProvider.getService().getPlugin(AndroidFamocoPlugin.PLUGIN_NAME)

  @Throws(KeyplePluginException::class)
  override suspend fun initCardReader(): CardReader {
    val readerPlugin = SmartCardServiceProvider.getService().getPlugin(AndroidNfcPlugin.PLUGIN_NAME)
    val cardReader = readerPlugin.getReader(AndroidNfcReader.READER_NAME)

    cardReader?.let {
      // with this protocol settings we activate the nfc for ISO1443_4 protocol
      (it as ConfigurableCardReader).activateProtocol(
          getContactlessIsoProtocol().readerProtocolName,
          getContactlessIsoProtocol().applicationProtocolName)

      this.cardReader = cardReader
    }

    (cardReader as ObservableCardReader).setReaderObservationExceptionHandler(
        readerObservationExceptionHandler)

    return cardReader
  }

  override suspend fun initSamReaders(): List<CardReader> {
    val samPlugin = SmartCardServiceProvider.getService().getPlugin(AndroidFamocoPlugin.PLUGIN_NAME)

    samReaders = mutableListOf()

    if (samPlugin != null) {
      val samReader = samPlugin.getReader(AndroidFamocoReader.READER_NAME)
      samReader?.let { samReaders.add(it) }
    }

    return samReaders
  }

  override fun getSamReader(): CardReader? {
    return if (samReaders.isNotEmpty()) {
      val filteredByName = samReaders.filter { it.name == AndroidFamocoReader.READER_NAME }

      return if (filteredByName.isNullOrEmpty()) {
        samReaders.first()
      } else {
        filteredByName.first()
      }
    } else {
      null
    }
  }

  override fun getSamPluginName(): String {
    return AndroidFamocoPlugin.PLUGIN_NAME
  }

  override fun getSamRegex(): String = SAM_READER_NAME_REGEX

  override fun getContactlessIsoProtocol(): CardReaderProtocol {
    return CardReaderProtocol(AndroidNfcSupportedProtocols.ISO_14443_4.name, "ISO_14443_4")
  }

  override fun getSamReaderProtocol(): String = "ISO_7816_3"

  override fun clear() {
    (cardReader as ConfigurableCardReader).deactivateProtocol(
        getContactlessIsoProtocol().readerProtocolName)

    successMedia.stop()
    successMedia.release()

    errorMedia.stop()
    errorMedia.release()
  }

  override fun displayResultSuccess(): Boolean {
    successMedia.start()
    return true
  }

  override fun displayResultFailed(): Boolean {
    errorMedia.start()
    return true
  }

  override fun getReaderConfiguratorSpi(): ReaderConfiguratorSpi = ReaderConfigurator()

  /**
   * Reader configurator used by the card resource service to setup the SAM reader with the required
   * settings.
   */
  internal class ReaderConfigurator : ReaderConfiguratorSpi {
    /** {@inheritDoc} */
    override fun setupReader(reader: CardReader) {
      // NOP
    }
  }

  companion object {
    const val SAM_READER_NAME_REGEX = ".*FamocoReader"
  }
}
