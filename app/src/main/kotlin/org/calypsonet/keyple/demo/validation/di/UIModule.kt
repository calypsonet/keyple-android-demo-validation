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
package org.calypsonet.keyple.demo.validation.di

import dagger.Module
import dagger.android.ContributesAndroidInjector
import org.calypsonet.keyple.demo.validation.di.scopes.ActivityScoped
import org.calypsonet.keyple.demo.validation.ui.activities.CardReaderActivity
import org.calypsonet.keyple.demo.validation.ui.activities.CardSummaryActivity
import org.calypsonet.keyple.demo.validation.ui.activities.HomeActivity
import org.calypsonet.keyple.demo.validation.ui.activities.SettingsActivity
import org.calypsonet.keyple.demo.validation.ui.activities.SplashScreenActivity

@Module
abstract class UIModule {
    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun splashScreenActivity(): SplashScreenActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun settingsActivity(): SettingsActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun homeActivity(): HomeActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun cardReaderActivity(): CardReaderActivity

    @ActivityScoped
    @ContributesAndroidInjector
    abstract fun cardSummaryActivity(): CardSummaryActivity
}
