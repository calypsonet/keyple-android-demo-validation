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
package org.eclipse.keyple.famoco.validator.ui.activities

import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import dagger.android.support.DaggerAppCompatActivity
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import kotlinx.android.synthetic.main.activity_card_summary.animation
import kotlinx.android.synthetic.main.activity_card_summary.bigText
import kotlinx.android.synthetic.main.activity_card_summary.mainView
import kotlinx.android.synthetic.main.activity_card_summary.mediumText
import kotlinx.android.synthetic.main.activity_card_summary.smallDesc
import org.eclipse.keyple.famoco.validator.R
import org.eclipse.keyple.famoco.validator.data.model.Status
import org.eclipse.keyple.famoco.validator.data.model.Status.Companion.getStatus

class CardSummaryActivity : DaggerAppCompatActivity() {

    private val timer = Timer()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_summary)
        val status =
            getStatus(
                intent.getStringExtra(STATUS_KEY)!!
            )
        when (status) {
            Status.TICKETS_FOUND -> {
                val df: DateFormat =
                    SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.ENGLISH)
                val date = df.format(Calendar.getInstance().time)
                mainView.setBackgroundColor(resources.getColor(R.color.green))
                animation.setAnimation("tick_white.json")
                bigText.setText(R.string.valid_main_desc)
                val nbTickets =
                    intent.getIntExtra(TICKETS_KEY, 0)
                if (nbTickets != 0) {
                    smallDesc.text = String.format(
                        getString(R.string.valid_small_desc),
                        date,
                        nbTickets
                    )
                } else {
                    smallDesc.text = String.format(
                        getString(R.string.valid_season_ticket_small_desc),
                        date,
                        intent.getStringExtra(CONTRACT)
                            ?.trim { it <= ' ' }
                    )
                }
                mediumText.setText(R.string.valid_last_desc)
                mediumText.visibility = View.VISIBLE
            }
            Status.INVALID_CARD -> {
                mainView.setBackgroundColor(resources.getColor(R.color.orange))
                animation.setAnimation("error_white.json")
                bigText.setText(R.string.card_invalid_main_desc)
                smallDesc.text = String.format(
                    getString(R.string.card_invalid_small_desc),
                    intent.getStringExtra(CARD_TYPE)
                        ?.trim { it <= ' ' }
                )
                mediumText.visibility = View.GONE
            }
            Status.EMPTY_CARD -> {
                mainView.setBackgroundColor(resources.getColor(R.color.red))
                animation.setAnimation("error_white.json")
                bigText.setText(R.string.no_tickets_main_desc)
                smallDesc.setText(R.string.no_tickets_small_desc)
                mediumText.visibility = View.GONE
            }
            else -> {
                mainView.setBackgroundColor(resources.getColor(R.color.red))
                animation.setAnimation("error_white.json")
                bigText.setText(R.string.error_main_desc)
                smallDesc.setText(R.string.error_small_desc)
                mediumText.visibility = View.GONE
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

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        timer.cancel()
    }

    companion object {
        const val STATUS_KEY = "status"
        const val TICKETS_KEY = "tickets"
        const val CONTRACT = "contract"
        const val CARD_TYPE = "cardtype"
        private const val RETURN_DELAY_MS = 6000
    }
}
