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
package org.eclipse.keyple.demo.validator.di

import android.app.Activity
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.eclipse.keyple.coppernic.ask.plugin.Cone2ContactReader
import org.eclipse.keyple.coppernic.ask.plugin.Cone2ContactlessReader
import org.eclipse.keyple.coppernic.ask.plugin.Cone2PluginFactory
import org.eclipse.keyple.coppernic.ask.plugin.ParagonSupportedContactProtocols
import org.eclipse.keyple.coppernic.ask.plugin.ParagonSupportedContactlessProtocols
import org.eclipse.keyple.core.plugin.AbstractLocalReader
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardService
import org.eclipse.keyple.core.service.event.ReaderObservationExceptionHandler
import org.eclipse.keyple.core.service.exception.KeypleException
import org.eclipse.keyple.demo.validator.reader.IReaderRepository
import org.eclipse.keyple.demo.validator.reader.PoReaderProtocol

/**
 *  @author youssefamrani
 */

class CoppernicReaderRepositoryImpl @Inject constructor(
    private val applicationContext: Context,
    private val readerObservationExceptionHandler: ReaderObservationExceptionHandler
) :
    IReaderRepository {

    override var poReader: Reader? = null
    override var samReaders: MutableMap<String, Reader> = mutableMapOf()

    @Throws(KeypleException::class)
    override fun registerPlugin(activity: Activity) {
        runBlocking {
            val pluginFactory: Cone2PluginFactory?
            pluginFactory = withContext(Dispatchers.IO) {
                Cone2PluginFactory.init(applicationContext, readerObservationExceptionHandler)
            }
            SmartCardService.getInstance().registerPlugin(pluginFactory)
        }
    }

    @Throws(KeypleException::class)
    override suspend fun initPoReader(): Reader? {
        val askPlugin =
            SmartCardService.getInstance().getPlugin(Cone2PluginFactory.pluginName)
        val poReader = askPlugin?.getReader(Cone2ContactlessReader.READER_NAME)
        poReader?.let {

            it.activateProtocol(
                getContactlessIsoProtocol()!!.readerProtocolName,
                getContactlessIsoProtocol()!!.applicationProtocolName
            )

            this.poReader = poReader
        }

        return poReader
    }

    @Throws(KeypleException::class)
    override suspend fun initSamReaders(): Map<String, Reader> {
        val askPlugin =
            SmartCardService.getInstance().getPlugin(Cone2PluginFactory.pluginName)
        samReaders = askPlugin?.readers?.filter {
            !it.value.isContactless
        }?.toMutableMap() ?: mutableMapOf()

        samReaders.forEach {
            (it.value as AbstractLocalReader).activateProtocol(
                getSamReaderProtocol(),
                getSamReaderProtocol()
            )
        }
        return samReaders
    }

    override fun getSamReader(): Reader? {
        return if (samReaders.isNotEmpty()) {
            val filteredByName = samReaders.filter {
                it.value.name == SAM_READER_1_NAME
            }

            return if (filteredByName.isNullOrEmpty()) {
                samReaders.values.first()
            } else {
                filteredByName.values.first()
            }
        } else {
            null
        }
    }

    override fun getContactlessIsoProtocol(): PoReaderProtocol? {
        return PoReaderProtocol(
            ParagonSupportedContactlessProtocols.ISO_14443.name,
            ParagonSupportedContactlessProtocols.ISO_14443.name
        )
    }

    override fun getContactlessMifareProtocol(): PoReaderProtocol? {
        return PoReaderProtocol(
            ParagonSupportedContactlessProtocols.MIFARE.name,
            ParagonSupportedContactlessProtocols.MIFARE.name
        )
    }

    override fun getSamReaderProtocol(): String =
        ParagonSupportedContactProtocols.INNOVATRON_HIGH_SPEED_PROTOCOL.name

    override fun clear() {
        poReader?.deactivateProtocol(getContactlessIsoProtocol()!!.readerProtocolName)

        samReaders.forEach {
            (it.value as AbstractLocalReader).deactivateProtocol(
                getSamReaderProtocol()
            )
        }
    }

    companion object {
        private const val SAM_READER_SLOT_1 = "1"
        const val SAM_READER_1_NAME =
            "${Cone2ContactReader.READER_NAME}_$SAM_READER_SLOT_1"
    }
}