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

import dagger.Module
import dagger.android.ContributesAndroidInjector
import org.eclipse.keyple.demo.validator.di.scopes.ActivityScoped
import org.eclipse.keyple.demo.validator.ui.activities.CardReaderActivity
import org.eclipse.keyple.demo.validator.ui.activities.CardSummaryActivity
import org.eclipse.keyple.demo.validator.ui.activities.HomeActivity
import org.eclipse.keyple.demo.validator.ui.activities.SettingsActivity
import org.eclipse.keyple.demo.validator.ui.activities.SplashScreenActivity

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