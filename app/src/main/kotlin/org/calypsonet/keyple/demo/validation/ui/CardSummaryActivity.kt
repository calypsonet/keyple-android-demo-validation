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
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.data.model.AppSettings
import org.calypsonet.keyple.demo.validation.data.model.CardReaderResponse
import org.calypsonet.keyple.demo.validation.data.model.ReaderType
import org.calypsonet.keyple.demo.validation.data.model.Status
import org.calypsonet.keyple.demo.validation.databinding.ActivityCardSummaryBinding
import timber.log.Timber

class CardSummaryActivity : BaseActivity() {

  private val timer = Timer()

  private lateinit var activityCardSummaryBinding: ActivityCardSummaryBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    activityCardSummaryBinding = ActivityCardSummaryBinding.inflate(layoutInflater)
    setContentView(activityCardSummaryBinding.root)
    val bundle = intent.getBundleExtra(Bundle::class.java.simpleName)!!
    val cardReaderResponse =
        bundle.getParcelable<CardReaderResponse>(CardReaderResponse::class.simpleName)
    when (cardReaderResponse?.status) {
      Status.SUCCESS -> {
        ticketingService.displayResultSuccess()
        activityCardSummaryBinding.animation.setAnimation("tick_white.json")
        activityCardSummaryBinding.mainView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.green))
        activityCardSummaryBinding.bigText.setText(R.string.valid_main_desc)
        val eventDate =
            cardReaderResponse.eventDateTime!!.format(
                DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", Locale.ENGLISH))
        activityCardSummaryBinding.locationTime.text =
            getString(
                R.string.valid_location_time,
                cardReaderResponse.validation?.location?.name,
                eventDate)
        val nbTickets = cardReaderResponse.nbTicketsLeft
        if (nbTickets != null) {
          activityCardSummaryBinding.smallDesc.text =
              when (nbTickets) {
                0 -> getString(R.string.valid_trips_left_zero)
                1 -> getString(R.string.valid_trips_left_single)
                else -> getString(R.string.valid_trips_left_multiple, nbTickets)
              }
        } else {
          val validityEndDate =
              cardReaderResponse.passValidityEndDate!!.format(
                  DateTimeFormatter.ofPattern("dd/MM/yyyy"))
          activityCardSummaryBinding.smallDesc.text =
              getString(R.string.valid_season_ticket, validityEndDate)
        }
        activityCardSummaryBinding.mediumText.setText(R.string.valid_last_desc)
      }
      Status.INVALID_CARD -> {
        ticketingService.displayResultFailed()
        activityCardSummaryBinding.animation.setAnimation("error_white.json")
        activityCardSummaryBinding.mainView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.orange))
        activityCardSummaryBinding.bigText.setText(R.string.card_invalid_main_desc)
        activityCardSummaryBinding.locationTime.text = cardReaderResponse.errorMessage
        activityCardSummaryBinding.mediumText.visibility = View.INVISIBLE
        activityCardSummaryBinding.smallDesc.visibility = View.INVISIBLE
      }
      Status.EMPTY_CARD -> {
        ticketingService.displayResultFailed()
        activityCardSummaryBinding.mainView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.red))
        activityCardSummaryBinding.animation.setAnimation("error_white.json")
        activityCardSummaryBinding.bigText.text = cardReaderResponse.errorMessage
        activityCardSummaryBinding.locationTime.setText(R.string.no_tickets_small_desc)
        activityCardSummaryBinding.mediumText.visibility = View.INVISIBLE
        activityCardSummaryBinding.smallDesc.visibility = View.INVISIBLE
      }
      else -> {
        ticketingService.displayResultFailed()
        activityCardSummaryBinding.mainView.setBackgroundColor(
            ContextCompat.getColor(this, R.color.red))
        activityCardSummaryBinding.animation.setAnimation("error_white.json")
        activityCardSummaryBinding.bigText.setText(R.string.error_main_desc)
        activityCardSummaryBinding.locationTime.text =
            cardReaderResponse?.errorMessage ?: getString(R.string.error_small_desc)
        activityCardSummaryBinding.mediumText.visibility = View.INVISIBLE
        activityCardSummaryBinding.smallDesc.visibility = View.INVISIBLE
      }
    }
    if (AppSettings.readerType == ReaderType.FLOWBIRD) {
      activityCardSummaryBinding.animation.repeatCount = 0
    }
    activityCardSummaryBinding.animation.playAnimation()
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
