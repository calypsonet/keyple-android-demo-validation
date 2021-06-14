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
package org.calypsonet.keyple.demo.validation.models

import java.util.Locale

enum class Status(private val status: String) {
    LOADING("loading"),
    SUCCESS("Success"),
    INVALID_CARD("Invalid card"),
    EMPTY_CARD("Empty card"),
    ERROR("error");

    override fun toString(): String {
        return status
    }

    companion object {
        @JvmStatic
        fun getStatus(name: String): Status {
            return try {
                valueOf(name.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                // If the given state does not exist, return the default value.
                ERROR
            }
        }
    }
}
