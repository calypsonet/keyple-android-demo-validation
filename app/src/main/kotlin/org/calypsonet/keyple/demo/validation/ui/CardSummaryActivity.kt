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
package org.calypsonet.keyple.demo.validation.ui

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.android.synthetic.main.activity_card_reader.*
import kotlinx.android.synthetic.main.activity_card_summary.animation
import kotlinx.android.synthetic.main.activity_card_summary.bigText
import kotlinx.android.synthetic.main.activity_card_summary.location_time
import kotlinx.android.synthetic.main.activity_card_summary.mainView
import kotlinx.android.synthetic.main.activity_card_summary.mediumText
import kotlinx.android.synthetic.main.activity_card_summary.smallDesc
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.AppSettings
import org.calypsonet.keyple.demo.validation.data.model.CardReaderResponse
import org.calypsonet.keyple.demo.validation.data.model.ReaderType
import org.calypsonet.keyple.demo.validation.data.model.Status
import timber.log.Timber

class CardSummaryActivity : BaseActivity() {

  private val timer = Timer()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_card_summary)
    val bundle = intent.getBundleExtra(Bundle::class.java.simpleName)!!
    val cardReaderResponse =
        bundle.getParcelable<CardReaderResponse>(CardReaderResponse::class.simpleName)
    when (cardReaderResponse?.status) {
      Status.SUCCESS -> {
        ticketingService.displayResultSuccess()
        animation.setAnimation("tick_white.json")
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.green))
        bigText.setText(R.string.valid_main_desc)
        val eventDate =
            cardReaderResponse.eventDateTime!!.format(
                DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.ENGLISH))
        location_time.text =
            getString(
                R.string.valid_location_time,
                cardReaderResponse.validation?.location?.name,
                eventDate)
        val nbTickets = cardReaderResponse.nbTicketsLeft
        if (nbTickets != null) {
          smallDesc.text =
              when (nbTickets) {
                0 -> getString(R.string.valid_trips_left_zero)
                1 -> getString(R.string.valid_trips_left_single)
                else -> getString(R.string.valid_trips_left_multiple, nbTickets)
              }
        } else {
          val validityEndDate =
              cardReaderResponse.passValidityEndDate!!.format(
                  DateTimeFormatter.ofPattern("dd/MM/yyyy"))
          smallDesc.text = getString(R.string.valid_season_ticket, validityEndDate)
        }
        mediumText.setText(R.string.valid_last_desc)
      }
      Status.INVALID_CARD -> {
        ticketingService.displayResultFailed()
        animation.setAnimation("error_white.json")
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))
        bigText.setText(R.string.card_invalid_main_desc)
        location_time.text = cardReaderResponse.errorMessage
        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
      Status.EMPTY_CARD -> {
        ticketingService.displayResultFailed()
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        animation.setAnimation("error_white.json")
        bigText.text = cardReaderResponse.errorMessage
        location_time.setText(R.string.no_tickets_small_desc)
        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
      else -> {
        ticketingService.displayResultFailed()
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        animation.setAnimation("error_white.json")
        bigText.setText(R.string.error_main_desc)
        location_time.text =
            cardReaderResponse?.errorMessage ?: getString(R.string.error_small_desc)
        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
    }
    if (AppSettings.readerType == ReaderType.FLOWBIRD) {
      animation.repeatCount = 0
    }
    animation.playAnimation()
    timer.schedule(
        object : TimerTask() {
          override fun run() {
            runOnUiThread { onBackPressed() }
          }
        },
        RETURN_DELAY_MS.toLong())
  }

  override fun onResume() {
    super.onResume()
    if (ticketingService.readersInitialized) {
      ticketingService.stopNfcDetection()
      Timber.d("stopNfcDetection")
    }
  }

  override fun onPause() {
    super.onPause()
    timer.cancel()
  }

  companion object {
    private const val RETURN_DELAY_MS = 6000
  }
}
