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
package org.eclipse.keyple.famoco.validator.di

import android.content.Context
import dagger.Module
import dagger.Provides
import org.eclipse.keyple.famoco.validator.di.scopes.AppScoped
import org.eclipse.keyple.famoco.validator.reader.IReaderRepository

/**
 *
 *  created on 18/09/2020
 *
 *  @author youssefamrani
 */

@Suppress("unused")
@Module
class ReaderModule {

    @Provides
    @AppScoped
    fun provideReaderRepository(context: Context): IReaderRepository = OmapiReaderRepositoryImpl(context)
}
