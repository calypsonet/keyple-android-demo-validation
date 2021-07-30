/********************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information regarding copyright
 * ownership.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/
package org.calypsonet.keyple.demo.validation.di.mock

import java.util.concurrent.ConcurrentHashMap
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi

internal class MockSamPluginAdapter :
    ObservablePluginSpi,
    MockSamPlugin {

    companion object {
        private const val MONITORING_CYCLE_DURATION_MS = 1000
    }

    private lateinit var seReaders: ConcurrentHashMap<String, ReaderSpi>

    override fun searchAvailableReaders(): MutableSet<ReaderSpi> {

        seReaders = ConcurrentHashMap()

        val sam = MockSamReaderAdapter()
        seReaders[sam.name] = sam

        return seReaders.map {
            it.value
        }.toMutableSet()
    }

    override fun searchReader(readerName: String?): ReaderSpi? {
        return if (seReaders.containsKey(readerName)) {
            seReaders[readerName]!!
        } else {
            null
        }
    }

    override fun searchAvailableReaderNames(): MutableSet<String> {
        return seReaders.map {
            it.key
        }.toMutableSet()
    }

    override fun getMonitoringCycleDuration(): Int {
        return MONITORING_CYCLE_DURATION_MS
    }

    override fun getName(): String = MockSamPlugin.PLUGIN_NAME

    override fun onUnregister() {
        // Do nothing
    }
}
