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
import org.eclipse.keyple.famoco.validator.di.scopes.ActivityScoped
import org.eclipse.keyple.famoco.validator.models.CardReaderResponse
import org.eclipse.keyple.famoco.validator.models.KeypleSettings
import org.eclipse.keyple.famoco.validator.models.Status
import org.eclipse.keyple.famoco.validator.ticketing.CalypsoInfo
import org.eclipse.keyple.famoco.validator.ticketing.TicketingSession
import timber.log.Timber
import java.util.Date
import java.util.Timer
import java.util.TimerTask
import javax.inject.Inject

@ActivityScoped
class CardReaderActivity : BaseActivity() {

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
                val bundle = Bundle()
                bundle.putParcelable(CardReaderResponse::class.simpleName, cardReaderResponse)
                intent.putExtra("bundle", bundle)
//                intent.putExtra(CardSummaryActivity.STATUS_KEY, cardReaderResponse.status.toString())
//                intent.putExtra(CardSummaryActivity.TICKETS_KEY, cardReaderResponse.nbTicketsLeft)
//                intent.putExtra(CardSummaryActivity.CONTRACT, cardReaderResponse.contract)
//                intent.putExtra(CardSummaryActivity.CARD_TYPE, cardReaderResponse.cardType)

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
                    val error = String.format(
                        getString(R.string.card_invalid_desc),
                        "a case of PO Not selected"
                    )
                    changeDisplay(
                        CardReaderResponse(
                            status = Status.INVALID_CARD,
                            contract = null,
                            cardType = null,
                            validation = null,
                            errorMessage = error
                        )
                    )
                    return
                }

                Timber.i("PO Type = ${ticketingSession.poTypeName}")
                if (CalypsoInfo.PO_TYPE_NAME_CALYPSO != ticketingSession.poTypeName) {
                    val cardType = ticketingSession.poTypeName ?: "Unknown card"
                    val error = String.format(
                        getString(R.string.card_invalid_small_desc),
                        cardType.trim { it <= ' ' }
                    )
                    changeDisplay(
                        CardReaderResponse(
                            status = Status.INVALID_CARD,
                            cardType = cardType,
                            contract = null,
                            validation = null,
                            errorMessage = error
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
                                val validationResult =
                                    ticketingSession.launchValidationProcedure(this@CardReaderActivity, locationFileManager.getLocations())
                                changeDisplay(validationResult)
                            }
                        } catch (e: IllegalStateException) {
                            Timber.e(e)
                            Timber.e("Load ERROR page after exception = ${e.message}")
                            changeDisplay(
                                CardReaderResponse(
                                    status = Status.ERROR,
                                    nbTicketsLeft = 0,
                                    contract = "",
                                    cardType = ticketingSession.poTypeName ?: "",
                                    validation = null,
                                    errorMessage = e.message
                                )
                            )
                        }
                    }
                    else -> {
                        //Do nothing
                    }
                }
            }
            else -> {
            }
        }
        Timber.i("New state = $currentAppState")
    }

    private fun showProgress() {
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
                val status: Status = Status.SUCCESS

                when (status) {
                    Status.SUCCESS -> changeDisplay(
                        CardReaderResponse(
                            status = status,
                            nbTicketsLeft = 7,
                            contract = "Season Pass",
                            cardType = CalypsoInfo.PO_TYPE_NAME_CALYPSO,
                            validation = null,
                            eventDate = Date()
                        )
                    )
                    Status.LOADING, Status.ERROR, Status.INVALID_CARD, Status.EMPTY_CARD -> changeDisplay(
                        CardReaderResponse(
                            status = status,
                            nbTicketsLeft = 0,
                            contract = "",
                            cardType = "",
                            validation = null,
                            errorMessage = "An error has occured during validation"
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
