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
package org.calypsonet.keyple.demo.validation.ui.activities

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import java.util.Timer
import java.util.TimerTask
import kotlinx.android.synthetic.main.activity_card_summary.animation
import kotlinx.android.synthetic.main.activity_card_summary.bigText
import kotlinx.android.synthetic.main.activity_card_summary.location_time
import kotlinx.android.synthetic.main.activity_card_summary.mainView
import kotlinx.android.synthetic.main.activity_card_summary.mediumText
import kotlinx.android.synthetic.main.activity_card_summary.smallDesc
import org.calypsonet.keyple.demo.common.parser.util.DateUtil
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.models.CardReaderResponse
import org.calypsonet.keyple.demo.validation.models.Status
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
        cardReaderApi.displayResultSuccess()
        animation.setAnimation("tick_white.json")
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.green))

        bigText.setText(R.string.valid_main_desc)

        val eventDate = DateUtil.formatDateToDisplayWithHour(cardReaderResponse.eventDate!!)
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
              DateUtil.formatDateToDisplay(cardReaderResponse.passValidityEndDate!!)
          smallDesc.text = getString(R.string.valid_season_ticket, validityEndDate)
        }

        mediumText.setText(R.string.valid_last_desc)
      }
      Status.INVALID_CARD -> {
        cardReaderApi.displayResultFailed()
        animation.setAnimation("error_white.json")
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.orange))

        bigText.setText(R.string.card_invalid_main_desc)
        location_time.text = cardReaderResponse.errorMessage

        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
      Status.EMPTY_CARD -> {
        cardReaderApi.displayResultFailed()
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        animation.setAnimation("error_white.json")

        bigText.text = cardReaderResponse.errorMessage
        location_time.setText(R.string.no_tickets_small_desc)

        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
      else -> {
        cardReaderApi.displayResultFailed()
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.red))
        animation.setAnimation("error_white.json")

        bigText.setText(R.string.error_main_desc)
        location_time.text =
            cardReaderResponse?.errorMessage ?: getString(R.string.error_small_desc)

        mediumText.visibility = View.INVISIBLE
        smallDesc.visibility = View.INVISIBLE
      }
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

    if (cardReaderApi.readersInitialized) {
      cardReaderApi.stopNfcDetection()
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
