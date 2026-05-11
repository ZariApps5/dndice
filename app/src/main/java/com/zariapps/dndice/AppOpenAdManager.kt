package com.zariapps.dndice

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd

class AppOpenAdManager(private val context: Context) {

    private val adUnitId = "ca-app-pub-3572341533498507/6677114247"
    private var appOpenAd: AppOpenAd? = null
    private var isLoading = false
    private var isShowing = false
    private var pendingShowActivity: Activity? = null

    fun loadAd() {
        if (isLoading || appOpenAd != null) return
        isLoading = true
        AppOpenAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoading = false
                    pendingShowActivity?.let {
                        pendingShowActivity = null
                        showAdIfAvailable(it)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w("AppOpenAdManager", "load failed: ${error.message}")
                    isLoading = false
                    pendingShowActivity = null
                }
            }
        )
    }

    fun showAdIfAvailable(activity: Activity) {
        if (isShowing) return
        val ad = appOpenAd
        if (ad == null) {
            if (isLoading) pendingShowActivity = activity
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowing = false
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowing = false
            }

            override fun onAdShowedFullScreenContent() {
                isShowing = true
            }
        }
        ad.show(activity)
    }
}
