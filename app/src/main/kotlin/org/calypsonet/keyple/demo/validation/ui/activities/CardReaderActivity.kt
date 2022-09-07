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

import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieDrawable
import java.util.Timer
import java.util.TimerTask
import kotlinx.android.synthetic.main.activity_card_reader.animation
import kotlinx.android.synthetic.main.activity_card_reader.mainView
import kotlinx.android.synthetic.main.activity_card_reader.presentCardTv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.di.scopes.ActivityScoped
import org.calypsonet.keyple.demo.validation.models.CardReaderResponse
import org.calypsonet.keyple.demo.validation.models.Status
import org.calypsonet.keyple.demo.validation.models.ValidationAppSettings
import org.calypsonet.keyple.demo.validation.ticketing.CalypsoInfo
import org.calypsonet.keyple.demo.validation.ticketing.ITicketingSession
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.spi.CardReaderObserverSpi
import timber.log.Timber

@ActivityScoped
class CardReaderActivity : BaseActivity() {

  private var cardReaderObserver: CardReaderObserver? = null

  @Suppress("DEPRECATION") private lateinit var progress: ProgressDialog

  private var timer = Timer()
  lateinit var ticketingSession: ITicketingSession
  var currentAppState = AppState.WAIT_SYSTEM_READY

  /* application states */
  enum class AppState {
    WAIT_SYSTEM_READY,
    WAIT_CARD,
    CARD_STATUS
  }

  @Suppress("DEPRECATION")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_card_reader)

    progress = ProgressDialog(this)
    progress.setMessage(getString(R.string.please_wait))
    progress.setCancelable(false)

    supportActionBar?.setDisplayHomeAsUpEnabled(true)
  }

  override fun onOptionsItemSelected(menuItem: MenuItem): Boolean {
    if (menuItem.itemId == android.R.id.home) {
      finish()
    }

    return super.onOptionsItemSelected(menuItem)
  }

  override fun onResume() {
    super.onResume()
    animation.playAnimation()

    if (!cardReaderApi.readersInitialized) {
      GlobalScope.launch {
        withContext(Dispatchers.Main) { showProgress() }

        withContext(Dispatchers.IO) {
          try {
            cardReaderObserver = CardReaderObserver()
            cardReaderApi.init(cardReaderObserver, this@CardReaderActivity)
            ticketingSession = cardReaderApi.getTicketingSession()!!
            cardReaderApi.readersInitialized = true
            handleAppEvents(AppState.WAIT_CARD, null)
            cardReaderApi.startNfcDetection()
          } catch (e: Exception) {
            Timber.e(e)
            withContext(Dispatchers.Main) {
              dismissProgress()
              showNoProxyReaderDialog(e)
            }
          }
        }
        if (cardReaderApi.readersInitialized) {
          withContext(Dispatchers.Main) { dismissProgress() }
        }
      }
    } else {
      cardReaderApi.startNfcDetection()
    }
    if (ValidationAppSettings.batteryPowered) {
      timer = Timer() // Need to reinit timer after cancel
      timer.schedule(
          object : TimerTask() {
            override fun run() {
              runOnUiThread { onBackPressed() }
            }
          },
          RETURN_DELAY_MS.toLong())
    }
  }

  override fun onDestroy() {
    cardReaderApi.readersInitialized = false
    cardReaderApi.onDestroy(cardReaderObserver)
    cardReaderObserver = null
    super.onDestroy()
  }

  override fun onPause() {
    super.onPause()
    animation.cancelAnimation()
    timer.cancel()
    if (cardReaderApi.readersInitialized) {
      cardReaderApi.stopNfcDetection()
      Timber.d("stopNfcDetection")
    }
  }

  private fun changeDisplay(cardReaderResponse: CardReaderResponse?) {
    if (cardReaderResponse != null) {
      if (cardReaderResponse.status === Status.LOADING) {
        presentCardTv.visibility = View.GONE
        mainView.setBackgroundColor(ContextCompat.getColor(this, R.color.turquoise))
        supportActionBar?.show()
        animation.playAnimation()
        animation.repeatCount = LottieDrawable.INFINITE
      } else {
        runOnUiThread { animation.cancelAnimation() }
        val intent = Intent(this, CardSummaryActivity::class.java)
        val bundle = Bundle()
        bundle.putParcelable(CardReaderResponse::class.simpleName, cardReaderResponse)
        intent.putExtra(Bundle::class.java.simpleName, bundle)
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
  private fun handleAppEvents(appState: AppState, readerEvent: CardReaderEvent?) {

    var newAppState = appState

    Timber.i(
        "Current state = $currentAppState, wanted new state = $newAppState, event = ${readerEvent?.type}")
    when (readerEvent?.type) {
      CardReaderEvent.Type.CARD_INSERTED, CardReaderEvent.Type.CARD_MATCHED -> {
        if (newAppState == AppState.WAIT_SYSTEM_READY) {
          return
        }
        Timber.i("Process default selection...")

        val seSelectionResult =
            ticketingSession.processDefaultSelection(readerEvent.scheduledCardSelectionsResponse)

        if (seSelectionResult.activeSelectionIndex == -1) {
          Timber.e("Card Not selected")
          val error = getString(R.string.card_invalid_desc)
          cardReaderApi.displayResultFailed()
          changeDisplay(
              CardReaderResponse(
                  status = Status.INVALID_CARD,
                  contract = null,
                  validation = null,
                  errorMessage = error))
          return
        }

        Timber.i("Card AID = ${ticketingSession.cardAid}")
        if (CalypsoInfo.AID_1TIC_ICA_1 != ticketingSession.cardAid &&
            CalypsoInfo.AID_1TIC_ICA_3 != ticketingSession.cardAid &&
            CalypsoInfo.AID_NORMALIZED_IDF != ticketingSession.cardAid) {
          val error = getString(R.string.card_invalid_desc)
          cardReaderApi.displayResultFailed()
          changeDisplay(
              CardReaderResponse(
                  status = Status.INVALID_CARD,
                  contract = null,
                  validation = null,
                  errorMessage = error))
          return
        }

        if (!ticketingSession.checkStructure()) {
          val error = getString(R.string.card_invalid_structure)
          changeDisplay(
              CardReaderResponse(
                  status = Status.INVALID_CARD,
                  contract = null,
                  validation = null,
                  errorMessage = error))
          return
        }

        Timber.i("A Calypso Card selection succeeded.")
        newAppState = AppState.CARD_STATUS
      }
      CardReaderEvent.Type.CARD_REMOVED -> {
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
        when (readerEvent?.type) {
          CardReaderEvent.Type.CARD_INSERTED, CardReaderEvent.Type.CARD_MATCHED -> {
            GlobalScope.launch {
              try {
                withContext(Dispatchers.Main) { progress.show() }

                val validationResult =
                    withContext(Dispatchers.IO) {
                      if (ticketingSession.checkStartupInfo()) {
                        ticketingSession.launchValidationProcedure(
                            this@CardReaderActivity, locationFileManager.getLocations())
                      } else {
                        null
                      }
                    }

                withContext(Dispatchers.Main) {
                  progress.dismiss()
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
                        validation = null,
                        errorMessage = e.message))
              }
            }
          }
          else -> {
            // Do nothing
          }
        }
      }
    }
    Timber.i("New state = $currentAppState")
  }

  private fun showProgress() {
    if (!progress.isShowing) {
      progress.show()
    }
  }

  private fun dismissProgress() {
    if (progress.isShowing) {
      progress.dismiss()
    }
  }

  private fun showNoProxyReaderDialog(t: Throwable) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.error_title)
    builder.setMessage(t.message)
    builder.setNegativeButton(R.string.quit) { _, _ -> finish() }
    val dialog = builder.create()
    dialog.setCancelable(false)
    dialog.show()
  }

  private inner class CardReaderObserver : CardReaderObserverSpi {

    override fun onReaderEvent(readerEvent: CardReaderEvent?) {
      Timber.i("New ReaderEvent received :${readerEvent?.type?.name}")
      handleAppEvents(currentAppState, readerEvent)
    }
  }

  companion object {
    private const val RETURN_DELAY_MS = 30000
  }
}
