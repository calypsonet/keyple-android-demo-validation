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
package org.calypsonet.keyple.famoco.validation.di

import android.app.Activity
import android.media.MediaPlayer
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.calypsonet.keyple.demo.validation.CardReader.CardReaderProtocol
import org.calypsonet.keyple.demo.validation.CardReader.IReaderRepository
import org.calypsonet.keyple.demo.validation.R
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
import org.eclipse.keyple.plugin.android.omapi.AndroidOmapiPlugin
import org.eclipse.keyple.plugin.android.omapi.AndroidOmapiPluginFactoryProvider
import timber.log.Timber

/** @author youssefamrani */
class OmapiReaderRepositoryImpl
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

    val smartCardService = SmartCardServiceProvider.getService()
    try {

      val nfcPluginFactory = AndroidNfcPluginFactoryProvider(activity).getFactory()
      smartCardService.registerPlugin(nfcPluginFactory)

      AndroidOmapiPluginFactoryProvider(activity) {
        SmartCardServiceProvider.getService().registerPlugin(it)
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  override fun getPlugin(): Plugin =
      SmartCardServiceProvider.getService().getPlugin(AndroidNfcPlugin.PLUGIN_NAME)

  override fun getSamRegex(): String = SAM_READER_NAME_REGEX

  @Throws(KeyplePluginException::class)
  override suspend fun initCardReader(): CardReader? {
    val readerPlugin = SmartCardServiceProvider.getService().getPlugin(AndroidNfcPlugin.PLUGIN_NAME)
    cardReader = readerPlugin.getReader(AndroidNfcReader.READER_NAME)

    // with this protocol settings we activate the nfc for ISO1443_4 protocol
    (cardReader as ConfigurableCardReader).activateProtocol(
        getContactlessIsoProtocol().readerProtocolName,
        getContactlessIsoProtocol().applicationProtocolName)

    (cardReader as ObservableCardReader).setReaderObservationExceptionHandler(
        readerObservationExceptionHandler)

    return cardReader
  }

  @Throws(KeyplePluginException::class)
  override suspend fun initSamReaders(): List<CardReader> {
    /*
     * Wait until OMAPI sam readers are available.
     * If we do not wait, no retries are made after calling 'SmartCardService.getInstance().getPlugin(PLUGIN_NAME).readers'
     * -> then no reader is returned
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    (runBlocking { delay(250) })
    for (x in 1..MAX_TRIES) {
      val readerPlugin = SmartCardServiceProvider.getService().getPlugin(getSamPluginName())
      samReaders = readerPlugin.readers.toMutableList()
      if (samReaders.isEmpty()) {
        Timber.d("No readers found in OMAPI Keyple Plugin")
        Timber.d("Retrying in 1 second")
        delay(1000)
      } else {
        Timber.d("Readers Found")
        break
      }
    }
    samReaders.forEach {
      if (getSamReaderProtocol()?.isNotEmpty() == true) {
        (it as ConfigurableCardReader).activateProtocol(
            getSamReaderProtocol(), getSamReaderProtocol())
      }
    }

    return samReaders
  }

  override fun getSamReader(): CardReader? {
    return if (samReaders.isNotEmpty()) {
      val filteredByName =
          samReaders.filter {
            // it.name == AndroidOmapiReader.READER_NAME_SIM_1
            // On Famoco FX205 the SAM reader name is AT901
            it.name == "AT901"
          }

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
    return AndroidOmapiPlugin.PLUGIN_NAME
  }

  override fun getContactlessIsoProtocol(): CardReaderProtocol {
    return CardReaderProtocol("ISO_7816", "ISO_7816")
  }

  override fun getSamReaderProtocol(): String? = null

  override fun clear() {
    if (getSamReaderProtocol()?.isNotEmpty() == true) {
      samReaders.forEach {
        (it as ConfigurableCardReader).deactivateProtocol(getSamReaderProtocol())
      }
    }

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

  override fun getReaderConfiguratorSpi(): ReaderConfiguratorSpi {
    return ReaderConfigurator()
  }

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
    private const val MAX_TRIES = 10
    // Should allow to
    const val SAM_READER_NAME_REGEX = ".*AT901*"
  }
}
