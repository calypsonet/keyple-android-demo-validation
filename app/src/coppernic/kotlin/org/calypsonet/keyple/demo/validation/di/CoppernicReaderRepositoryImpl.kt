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
import android.content.Context
import android.media.MediaPlayer
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.demo.validation.CardReader.CardReaderProtocol
import org.calypsonet.keyple.demo.validation.CardReader.IReaderRepository
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.plugin.coppernic.Cone2ContactReader
import org.calypsonet.keyple.plugin.coppernic.Cone2ContactlessReader
import org.calypsonet.keyple.plugin.coppernic.Cone2Plugin
import org.calypsonet.keyple.plugin.coppernic.Cone2PluginFactory
import org.calypsonet.keyple.plugin.coppernic.Cone2PluginFactoryProvider
import org.calypsonet.keyple.plugin.coppernic.ParagonSupportedContactlessProtocols
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.ConfigurableCardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.spi.CardReaderObservationExceptionHandlerSpi
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.resource.spi.ReaderConfiguratorSpi

/** @author youssefamrani */
class CoppernicReaderRepositoryImpl
@Inject
constructor(
    private val applicationContext: Context,
    private val readerObservationExceptionHandler: CardReaderObservationExceptionHandlerSpi
) : IReaderRepository {

  lateinit var successMedia: MediaPlayer
  lateinit var errorMedia: MediaPlayer

  override var cardReader: CardReader? = null
  override var samReaders: MutableList<CardReader> = mutableListOf()

  @Throws(KeyplePluginException::class)
  override fun registerPlugin(activity: Activity) {
    runBlocking {
      successMedia = MediaPlayer.create(activity, R.raw.success)
      errorMedia = MediaPlayer.create(activity, R.raw.error)

      val pluginFactory: Cone2PluginFactory?
      pluginFactory =
          withContext(Dispatchers.IO) { Cone2PluginFactoryProvider.getFactory(applicationContext) }

      SmartCardServiceProvider.getService().registerPlugin(pluginFactory)
    }
  }

  override fun getPlugin(): Plugin =
      SmartCardServiceProvider.getService().getPlugin(Cone2Plugin.PLUGIN_NAME)

  @Throws(KeyplePluginException::class)
  override suspend fun initCardReader(): CardReader? {
    val askPlugin = SmartCardServiceProvider.getService().getPlugin(Cone2Plugin.PLUGIN_NAME)
    val cardReader = askPlugin?.getReader(Cone2ContactlessReader.READER_NAME)
    cardReader?.let {
      (it as ConfigurableCardReader).activateProtocol(
          getContactlessIsoProtocol().readerProtocolName,
          getContactlessIsoProtocol().applicationProtocolName)

      (cardReader as ObservableCardReader).setReaderObservationExceptionHandler(
          readerObservationExceptionHandler)

      this.cardReader = cardReader
    }

    return cardReader
  }

  @Throws(KeyplePluginException::class)
  override suspend fun initSamReaders(): List<CardReader> {
    val askPlugin = SmartCardServiceProvider.getService().getPlugin(Cone2Plugin.PLUGIN_NAME)
    samReaders =
        askPlugin?.readers?.filter { !it.isContactless }?.toMutableList() ?: mutableListOf()

    samReaders.forEach {
      (it as ConfigurableCardReader).activateProtocol(
          getSamReaderProtocol(), getSamReaderProtocol())
    }
    return samReaders
  }

  override fun getSamReader(): CardReader? {
    return if (samReaders.isNotEmpty()) {
      val filteredByName = samReaders.filter { it.name == SAM_READER_1_NAME }

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
    return Cone2Plugin.PLUGIN_NAME
  }

  override fun getContactlessIsoProtocol(): CardReaderProtocol {
    return CardReaderProtocol(
        ParagonSupportedContactlessProtocols.ISO_14443.name,
        ParagonSupportedContactlessProtocols.ISO_14443.name)
  }

  override fun getSamReaderProtocol(): String = "ISO_7816_3"

  override fun getSamRegex(): String = SAM_READER_NAME_REGEX

  override fun getReaderConfiguratorSpi(): ReaderConfiguratorSpi = ReaderConfigurator()

  override fun clear() {
    (cardReader as ConfigurableCardReader).deactivateProtocol(
        getContactlessIsoProtocol().readerProtocolName)

    samReaders.forEach { (it as ConfigurableCardReader).deactivateProtocol(getSamReaderProtocol()) }

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

  companion object {
    private const val SAM_READER_SLOT_1 = "1"
    const val SAM_READER_1_NAME = "${Cone2ContactReader.READER_NAME}_$SAM_READER_SLOT_1"

    const val SAM_READER_NAME_REGEX = ".*ContactReader_1"
  }

  /**
   * CardReader configurator used by the card resource service to setup the SAM CardReader with the
   * required settings.
   */
  internal class ReaderConfigurator : ReaderConfiguratorSpi {
    /** {@inheritDoc} */
    override fun setupReader(CardReader: CardReader) {
      // NOP
    }
  }
}
