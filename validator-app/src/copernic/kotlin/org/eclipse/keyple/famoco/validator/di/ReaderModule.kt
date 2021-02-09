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

import android.content.Context
import dagger.Module
import dagger.Provides
import org.eclipse.keyple.core.service.event.ReaderObservationExceptionHandler
import org.eclipse.keyple.demo.validator.di.scopes.AppScoped
import org.eclipse.keyple.demo.validator.reader.IReaderRepository
import timber.log.Timber

/**
 *  @author youssefamrani
 */

@Suppress("unused")
@Module
class ReaderModule {

    @Provides
    fun provideReaderRepository(
        context: Context,
        readerObservationExceptionHandler: ReaderObservationExceptionHandler
    ): IReaderRepository {
        return CoppernicReaderRepositoryImpl(context, readerObservationExceptionHandler)
    }

    @Provides
    @AppScoped
    fun provideReaderObservationExceptionHandler(): ReaderObservationExceptionHandler =
        ReaderObservationExceptionHandler { pluginName, readerName, e ->
            Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
        }
}
