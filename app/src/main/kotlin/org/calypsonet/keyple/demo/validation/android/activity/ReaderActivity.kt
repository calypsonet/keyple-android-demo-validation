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
package org.calypsonet.keyple.demo.validation.android.activity

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
import org.calypsonet.keyple.demo.validation.ApplicationSettings
import org.calypsonet.keyple.demo.validation.R
import org.calypsonet.keyple.demo.validation.android.di.scope.ActivityScoped
import org.calypsonet.keyple.demo.validation.service.ticketing.CalypsoInfo
import org.calypsonet.keyple.demo.validation.service.ticketing.TicketingService
import org.calypsonet.keyple.demo.validation.service.ticketing.model.CardReaderResponse
import org.calypsonet.keyple.demo.validation.service.ticketing.model.Status
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.spi.CardReaderObserverSpi
import timber.log.Timber

@ActivityScoped
class ReaderActivity : BaseActivity() {

  @Suppress("DEPRECATION") private lateinit var progress: ProgressDialog
  private var cardReaderObserver: CardReaderObserver? = null
  private lateinit var ticketingService: TicketingService
  var currentAppState = AppState.WAIT_SYSTEM_READY
  private var timer = Timer()

  /* application states */
  enum class AppState {
    WAIT_SYSTEM_READY,
    WAIT_CARD,
    CARD_STATUS
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_card_reader)
    @Suppress("DEPRECATION")
    progress = ProgressDialog(this)
    @Suppress("DEPRECATION")
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
    if (!mainService.readersInitialized) {
      GlobalScope.launch {
        withContext(Dispatchers.Main) { showProgress() }
        withContext(Dispatchers.IO) {
          try {
            cardReaderObserver = CardReaderObserver()
            mainService.init(
                cardReaderObserver, this@ReaderActivity, ApplicationSettings.readerType)
            mainService.readersInitialized = true
            handleAppEvents(AppState.WAIT_CARD, null)
            mainService.startNfcDetection()
          } catch (e: Exception) {
            Timber.e(e)
            withContext(Dispatchers.Main) {
              dismissProgress()
              showNoProxyReaderDialog(e)
            }
          }
        }
        if (mainService.readersInitialized) {
          withContext(Dispatchers.Main) { dismissProgress() }
        }
      }
    } else {
      mainService.startNfcDetection()
    }
    if (ApplicationSettings.batteryPowered) {
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

  override fun onPause() {
    super.onPause()
    animation.cancelAnimation()
    timer.cancel()
    if (mainService.readersInitialized) {
      mainService.stopNfcDetection()
      Timber.d("stopNfcDetection")
    }
  }

  override fun onDestroy() {
    mainService.readersInitialized = false
    mainService.onDestroy(cardReaderObserver)
    cardReaderObserver = null
    super.onDestroy()
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
            ticketingService.parseScheduledCardSelectionsResponse(
                readerEvent.scheduledCardSelectionsResponse)
        if (seSelectionResult.activeSelectionIndex == -1) {
          Timber.e("Card Not selected")
          val error = getString(R.string.card_invalid_aid)
          mainService.displayResultFailed()
          changeDisplay(
              CardReaderResponse(
                  status = Status.INVALID_CARD,
                  contract = null,
                  validation = null,
                  errorMessage = error))
          return
        }
        Timber.i("Card AID = ${ticketingService.cardAid}")
        if (CalypsoInfo.AID_1TIC_ICA_1 != ticketingService.cardAid &&
            CalypsoInfo.AID_1TIC_ICA_3 != ticketingService.cardAid &&
            CalypsoInfo.AID_NORMALIZED_IDF != ticketingService.cardAid) {
          val error = getString(R.string.card_invalid_aid)
          mainService.displayResultFailed()
          changeDisplay(
              CardReaderResponse(
                  status = Status.INVALID_CARD,
                  contract = null,
                  validation = null,
                  errorMessage = error))
          return
        }
        if (!ticketingService.checkStructure()) {
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
                      ticketingService.launchValidationProcedure(
                          this@ReaderActivity, locationFileService.getLocations())
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

  private fun showNoProxyReaderDialog(t: Throwable) {
    val builder = AlertDialog.Builder(this)
    builder.setTitle(R.string.error_title)
    builder.setMessage(t.message)
    builder.setNegativeButton(R.string.quit) { _, _ -> finish() }
    val dialog = builder.create()
    dialog.setCancelable(false)
    dialog.show()
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

  companion object {
    private const val RETURN_DELAY_MS = 30000
  }

  private inner class CardReaderObserver : CardReaderObserverSpi {

    override fun onReaderEvent(readerEvent: CardReaderEvent?) {
      Timber.i("New ReaderEvent received :${readerEvent?.type?.name}")
      handleAppEvents(currentAppState, readerEvent)
    }
  }
}
