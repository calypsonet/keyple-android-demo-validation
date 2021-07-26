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
package org.calypsonet.keyple.demo.validation.util

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import java.util.concurrent.atomic.AtomicBoolean

class LiveEvent<T> : MutableLiveData<T>() {

    private val pending = AtomicBoolean(false)
    private val observers = mutableSetOf<Observer<in T>>()

    private val internalObserver = Observer<T> { t ->
        if (pending.compareAndSet(true, false)) {
            observers.forEach { observer ->
                observer.onChanged(t)
            }
        }
    }

    @MainThread
    override fun observe(owner: LifecycleOwner, observer: Observer<in T>) {
        observers.add(observer)

        if (!hasObservers()) {
            super.observe(owner, internalObserver)
        }
    }

    override fun removeObservers(owner: LifecycleOwner) {
        observers.clear()
        super.removeObservers(owner)
    }

    override fun removeObserver(observer: Observer<in T>) {
        observers.remove(observer)
        super.removeObserver(observer)
    }

    @MainThread
    override fun setValue(t: T?) {
        pending.set(true)
        super.setValue(t)
    }

    @MainThread
    fun call() {
        value = null
    }
}
