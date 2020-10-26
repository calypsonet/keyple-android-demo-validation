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

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.airbnb.lottie.LottieDrawable
import dagger.android.support.DaggerAppCompatActivity
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject
import kotlinx.android.synthetic.main.activity_card_reader.animation
import kotlinx.android.synthetic.main.activity_card_reader.mainView
import kotlinx.android.synthetic.main.activity_card_reader.presentCardTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.keyple.core.service.event.ObservableReader
import org.eclipse.keyple.core.service.event.ReaderEvent
import org.eclipse.keyple.core.service.exception.KeyplePluginInstantiationException
import org.eclipse.keyple.famoco.validator.BuildConfig
import org.eclipse.keyple.famoco.validator.R
import org.eclipse.keyple.famoco.validator.data.CardReaderApi
import org.eclipse.keyple.famoco.validator.data.model.CardReaderResponse
import org.eclipse.keyple.famoco.validator.data.model.Status
import org.eclipse.keyple.famoco.validator.di.scopes.ActivityScoped
import org.eclipse.keyple.famoco.validator.ticketing.ITicketingSession
import org.eclipse.keyple.famoco.validator.ticketing.TicketingSession
import org.eclipse.keyple.famoco.validator.util.KeypleSettings
import timber.log.Timber

@ActivityScoped
class CardReaderActivity : DaggerAppCompatActivity() {

    @Inject
    lateinit var cardReaderApi: CardReaderApi

    private var poReaderObserver: PoObserver? = null

    private lateinit var progress: ProgressDialog
    private var timer = Timer()
    private var readersInitialized = false
    lateinit var ticketingSession: TicketingSession
    var currentAppState = AppState.WAIT_SYSTEM_READY

    /* application states */
    enum class AppState {
        UNSPECIFIED, WAIT_SYSTEM_READY, WAIT_CARD, CARD_STATUS
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_reader)

        progress = ProgressDialog(this)
        progress.setMessage(getString(R.string.please_wait))
        progress.setCancelable(false)
    }

    override fun onResume() {
        super.onResume()
        animation.playAnimation()

        if (!readersInitialized) {
            GlobalScope.launch {
                withContext(Dispatchers.Main) {
                    showProgress()
                }

                withContext(Dispatchers.IO) {
                    try {
                        poReaderObserver = PoObserver()
                        cardReaderApi.init(poReaderObserver, this@CardReaderActivity)
                        ticketingSession = cardReaderApi.getTicketingSession()!!
                        readersInitialized = true
                        handleAppEvents(AppState.WAIT_CARD, null)
                        cardReaderApi.startNfcDetection()
                    } catch (e: KeyplePluginInstantiationException) {
                        Timber.e(e)
                        withContext(Dispatchers.Main) {
                            dismissProgress()
                            showNoProxyReaderDialog(e)
                        }
                    } catch (e: IllegalStateException) {
                        Timber.e(e)
                        withContext(Dispatchers.Main) {
                            dismissProgress()
                            showNoProxyReaderDialog(e)
                        }
                    }
                }
                if (readersInitialized) {
                    withContext(Dispatchers.Main) {
                        dismissProgress()
                        updateReaderInfos()
                    }
                }
            }
        } else {
            cardReaderApi.startNfcDetection()
        }
        if (KeypleSettings.batteryPowered) {
            timer = Timer() // Need to reinit timer after cancel
            timer.schedule(object : TimerTask() {
                override fun run() {
                    runOnUiThread { onBackPressed() }
                }
            }, RETURN_DELAY_MS.toLong())
        }
    }

    override fun onDestroy() {
        readersInitialized = false
        cardReaderApi.onDestroy(poReaderObserver)
        poReaderObserver = null
        super.onDestroy()
    }

    fun updateReaderInfos() {

        @Suppress("ConstantConditionIf")
        val readerPlugin = if (BuildConfig.FLAVOR == "copernic") {
            BuildConfig.FLAVOR
        } else {
            "Android NFC - ${BuildConfig.FLAVOR}"
        }
    }

    override fun onPause() {
        super.onPause()
        animation.cancelAnimation()
        timer.cancel()
        if (readersInitialized) {
            cardReaderApi.stopNfcDetection()
            Timber.d("stopNfcDetection")
        }
    }

    private fun changeDisplay(cardReaderResponse: CardReaderResponse?) {
        if (cardReaderResponse != null) {
            if (cardReaderResponse.status === Status.LOADING) {
                presentCardTv.visibility = View.GONE
                mainView.setBackgroundColor(resources.getColor(R.color.turquoise))
                supportActionBar?.show()
                animation.playAnimation()
                animation.repeatCount = LottieDrawable.INFINITE
            } else {
                runOnUiThread {
                    animation.cancelAnimation()
                }
                val intent = Intent(this, CardSummaryActivity::class.java)
                intent.putExtra(
                    CardSummaryActivity.STATUS_KEY,
                    cardReaderResponse.status.toString()
                )
                intent.putExtra(CardSummaryActivity.TICKETS_KEY, cardReaderResponse.ticketsNumber)
                intent.putExtra(CardSummaryActivity.CONTRACT, cardReaderResponse.contract)
                intent.putExtra(CardSummaryActivity.CARD_TYPE, cardReaderResponse.cardType)
                startActivity(intent)
            }
        } else {
            presentCardTv.visibility = View.VISIBLE
        }
    }

    /**
     * main app state machine handle
     *
     * @param appState
     * @param readerEvent
     */
    private fun handleAppEvents(appState: AppState, readerEvent: ReaderEvent?) {

        var newAppState = appState

        Timber.i("Current state = $currentAppState, wanted new state = $newAppState, event = ${readerEvent?.eventType}")
        when (readerEvent?.eventType) {
            ReaderEvent.EventType.CARD_INSERTED, ReaderEvent.EventType.CARD_MATCHED -> {
                if (newAppState == AppState.WAIT_SYSTEM_READY) {
                    return
                }
                Timber.i("Process default selection...")

                val seSelectionResult =
                    ticketingSession.processDefaultSelection(readerEvent.defaultSelectionsResponse)

                if (!seSelectionResult.hasActiveSelection()) {
                    Timber.e("PO Not selected")
                    changeDisplay(CardReaderResponse(Status.INVALID_CARD, 0, "", ""))
                    return
                }

                Timber.i("PO Type = ${ticketingSession.poTypeName}")
                if ("CALYPSO" != ticketingSession.poTypeName) {
                    changeDisplay(
                        CardReaderResponse(
                            Status.INVALID_CARD,
                            0,
                            "",
                            ticketingSession.poTypeName ?: ""
                        )
                    )
                    return
                } else {
                    Timber.i("A Calypso PO selection succeeded.")
                    newAppState = AppState.CARD_STATUS
                }
            }
            ReaderEvent.EventType.CARD_REMOVED -> {
                currentAppState = AppState.WAIT_SYSTEM_READY
            }
            else -> {
                Timber.w("Event type not handled.")
            }
        }

        when (newAppState) {
            AppState.WAIT_SYSTEM_READY, AppState.WAIT_CARD -> {
                currentAppState = newAppState
            }
            AppState.CARD_STATUS -> {
                currentAppState = newAppState
                when (readerEvent?.eventType) {
                    ReaderEvent.EventType.CARD_INSERTED, ReaderEvent.EventType.CARD_MATCHED -> {
                        try {
                            if (ticketingSession.analyzePoProfile()) {
                                val cardContent = ticketingSession.cardContent
                                val contract = String(cardContent.contracts[1] ?: byteArrayOf(0))
                                Timber.i("Contract =  $contract")
                                if (contract.isEmpty() || contract.contains("NO CONTRACT") || !contract.contains(
                                        "SEASON"
                                    )
                                ) {
                                    // index des counters commence à un
                                    cardContent.counters[1]?.let {
                                        if (it > 0) {
                                            if (ticketingSession.debitTickets(1) == ITicketingSession.STATUS_OK) {
                                                Timber.i("Debit TICKETS_FOUND page.")
                                                changeDisplay(
                                                    CardReaderResponse(
                                                        Status.TICKETS_FOUND,
                                                        it - 1,
                                                        "",
                                                        ticketingSession.poTypeName ?: ""
                                                    )
                                                )
                                            } else {
                                                Timber.i("Debit ERROR page.")
                                                changeDisplay(
                                                    CardReaderResponse(
                                                        Status.ERROR,
                                                        0,
                                                        "",
                                                        ticketingSession.poTypeName ?: ""
                                                    )
                                                )
                                            }
                                        } else {
                                            Timber.i("Load EMPTY_CARD page.")
                                            changeDisplay(
                                                CardReaderResponse(
                                                    Status.EMPTY_CARD,
                                                    0,
                                                    "",
                                                    ticketingSession.poTypeName ?: ""
                                                )
                                            )
                                        }
                                    }
                                } else {
                                    if (ticketingSession.loadTickets(0) == ITicketingSession.STATUS_OK) {
                                        Timber.i("Season TICKETS_FOUND page.")
                                        changeDisplay(
                                            CardReaderResponse(
                                                Status.TICKETS_FOUND,
                                                0,
                                                contract,
                                                ticketingSession.poTypeName ?: ""
                                            )
                                        )
                                    } else {
                                        Timber.i("Season ticket ERROR page.")
                                        changeDisplay(
                                            CardReaderResponse(
                                                Status.ERROR,
                                                0,
                                                "",
                                                ticketingSession.poTypeName ?: ""
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (e: IllegalStateException) {
                            Timber.e(e)
                            Timber.e("Load ERROR page after exception = ${e.message}")
                            changeDisplay(
                                CardReaderResponse(
                                    Status.ERROR,
                                    0,
                                    "",
                                    ticketingSession.poTypeName ?: ""
                                )
                            )
                        }
                    }
                }
            }
            else -> {
            }
        }
        Timber.i("New state = $currentAppState")
    }

    fun showProgress() {
        if (!progress.isShowing) {
            progress.show()
        }
    }

    fun dismissProgress() {
        if (progress.isShowing) {
            progress.dismiss()
        }
    }

    fun showNoProxyReaderDialog(t: Throwable) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.error_title)
        builder.setMessage(t.message)
        builder.setNegativeButton(R.string.quit) { _, _ ->
            finish()
        }
        val dialog = builder.create()
        dialog.setCancelable(false)
        dialog.show()
    }

    private inner class PoObserver : ObservableReader.ReaderObserver {
        override fun update(event: ReaderEvent) {
            Timber.i("New ReaderEvent received :${event.eventType.name}")

            if (event.eventType == ReaderEvent.EventType.CARD_MATCHED &&
                cardReaderApi.isMockedResponse()
            ) {
                launchMockedEvents()
            } else {
                handleAppEvents(currentAppState, event)
            }
        }
    }

    /**
     * Used to mock card responses -> display chosen result screen
     */
    private fun launchMockedEvents() {
        Timber.i("Launch STUB Card event !!")
        // STUB Card event
        val timer = Timer()
        timer.schedule(object : TimerTask() {
            override fun run() {
                /** Change this value to see other status screens **/
                val status: Status = Status.TICKETS_FOUND
                when (status) {
                    Status.TICKETS_FOUND -> changeDisplay(
                        CardReaderResponse(
                            Status.TICKETS_FOUND,
                            7,
                            "Season Pass",
                            ""
                        )
                    )
                    Status.LOADING, Status.ERROR, Status.SUCCESS, Status.INVALID_CARD, Status.EMPTY_CARD -> changeDisplay(
                        CardReaderResponse(
                            status,
                            0,
                            "",
                            ""
                        )
                    )
                }
            }
        }, EVENT_DELAY_MS.toLong())
    }

    companion object {
        private const val RETURN_DELAY_MS = 30000
        private const val EVENT_DELAY_MS = 500
    }
}
