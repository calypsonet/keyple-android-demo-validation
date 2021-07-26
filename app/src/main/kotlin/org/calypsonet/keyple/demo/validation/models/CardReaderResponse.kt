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
package org.calypsonet.keyple.demo.validation.models

import android.os.Parcelable
import java.util.Date
import kotlinx.android.parcel.Parcelize

@Parcelize
data class CardReaderResponse(
    val status: Status,
    val nbTicketsLeft: Int? = null,
    val contract: String?,
    val cardType: String?,
    val validation: Validation?,
    val eventDate: Date? = null,
    val passValidityEndDate: Date? = null,
    val errorMessage: String? = null
) : Parcelable
