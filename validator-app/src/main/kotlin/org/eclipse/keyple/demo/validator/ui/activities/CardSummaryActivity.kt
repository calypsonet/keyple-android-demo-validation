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
package org.eclipse.keyple.demo.validator.ui.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import kotlinx.android.synthetic.main.activity_card_summary.animation
import kotlinx.android.synthetic.main.activity_card_summary.bigText
import kotlinx.android.synthetic.main.activity_card_summary.location_time
import kotlinx.android.synthetic.main.activity_card_summary.mainView
import kotlinx.android.synthetic.main.activity_card_summary.mediumText
import kotlinx.android.synthetic.main.activity_card_summary.smallDesc
import org.eclipse.keyple.demo.validator.R
import org.eclipse.keyple.demo.validator.models.CardReaderResponse
import org.eclipse.keyple.demo.validator.models.Status
import org.eclipse.keyple.parser.utils.DateUtils
import java.util.Timer
import java.util.TimerTask

class CardSummaryActivity : BaseActivity() {

    private val timer = Timer()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_summary)

        val bundle = intent.getBundleExtra("bundle")!!
        val cardReaderResponse =
            bundle.getParcelable<CardReaderResponse>(CardReaderResponse::class.simpleName)

        when (cardReaderResponse?.status) {
            Status.SUCCESS -> {
                animation.setAnimation("tick_white.json")
                mainView.setBackgroundColor(resources.getColor(R.color.green))

                bigText.setText(R.string.valid_main_desc)

                val eventDate =
                    DateUtils.formatDateToDisplayWithHour(cardReaderResponse.eventDate!!)
                location_time.text = getString(
                    R.string.valid_location_time,
                    cardReaderResponse.validation?.location?.name,
                    eventDate
                )

                val nbTickets = cardReaderResponse.nbTicketsLeft
                if (nbTickets != null) {
                    smallDesc.text = when (nbTickets) {
                        0 -> getString(R.string.valid_trips_left_zero)
                        1 -> getString(R.string.valid_trips_left_single)
                        else -> getString(R.string.valid_trips_left_multiple, nbTickets)
                    }
                } else {
                    val validityEndDate =
                        DateUtils.formatDateToDisplay(cardReaderResponse.passValidityEndDate!!)
                    smallDesc.text = getString(R.string.valid_season_ticket, validityEndDate)
                }

                mediumText.setText(R.string.valid_last_desc)
            }
            Status.INVALID_CARD -> {
                animation.setAnimation("error_white.json")
                mainView.setBackgroundColor(resources.getColor(R.color.orange))

                bigText.setText(R.string.card_invalid_main_desc)
                location_time.text = cardReaderResponse.errorMessage

                mediumText.visibility = View.INVISIBLE
                smallDesc.visibility = View.INVISIBLE
            }
            Status.EMPTY_CARD -> {
                mainView.setBackgroundColor(resources.getColor(R.color.red))
                animation.setAnimation("error_white.json")

                bigText.text = cardReaderResponse.errorMessage
                location_time.setText(R.string.no_tickets_small_desc)

                mediumText.visibility = View.INVISIBLE
                smallDesc.visibility = View.INVISIBLE
            }
            else -> {
                mainView.setBackgroundColor(resources.getColor(R.color.red))
                animation.setAnimation("error_white.json")

                bigText.setText(R.string.error_main_desc)
                location_time.setText(R.string.error_small_desc)

                mediumText.visibility = View.INVISIBLE
                smallDesc.visibility = View.INVISIBLE
            }
        }

        animation.playAnimation()

        // Play sound
        val mp =
            MediaPlayer.create(this, R.raw.reading_sound)
        mp.start()
        timer.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread { onBackPressed() }
            }
        }, RETURN_DELAY_MS.toLong())
    }

    override fun onPause() {
        super.onPause()
        timer.cancel()
    }

    companion object {
        private const val RETURN_DELAY_MS = 6000
    }
}
