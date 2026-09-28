package com.loudwake.app.billing

import android.app.Application
import com.loudwake.app.BuildConfig
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith

object DayStartBilling {
    @Volatile
    var isPremium: Boolean = false
        private set

    private val configured: Boolean
        get() = BuildConfig.REVENUECAT_PUBLIC_API_KEY.isNotBlank()

    fun configure(app: Application) {
        val key = BuildConfig.REVENUECAT_PUBLIC_API_KEY
        if (key.isBlank()) return
        if (Purchases.isConfigured) {
            refreshCustomerInfo()
            return
        }
        Purchases.configure(PurchasesConfiguration.Builder(app, key).build())
        refreshCustomerInfo()
    }

    fun refreshCustomerInfo(onDone: ((Boolean) -> Unit)? = null) {
        if (!configured) {
            onDone?.invoke(false)
            return
        }
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { onDone?.invoke(isPremium) },
            onSuccess = { info ->
                isPremium = info.hasActiveEntitlement()
                onDone?.invoke(isPremium)
            }
        )
    }

    fun purchaseDefaultPackage(
        activity: android.app.Activity,
        onResult: (Boolean) -> Unit
    ) {
        if (!configured) {
            onResult(false)
            return
        }
        Purchases.sharedInstance.getOfferingsWith(
            onError = { onResult(false) },
            onSuccess = { offerings ->
                val pkg = offerings.current?.availablePackages?.firstOrNull()
                if (pkg == null) {
                    onResult(false)
                    return@getOfferingsWith
                }
                Purchases.sharedInstance.purchaseWith(
                    PurchaseParams.Builder(activity, pkg).build(),
                    onError = { _, _ -> onResult(false) },
                    onSuccess = { _, customerInfo ->
                        isPremium = customerInfo.hasActiveEntitlement()
                        onResult(isPremium)
                    }
                )
            }
        )
    }

    fun billingReady(): Boolean = configured

    private fun CustomerInfo.hasActiveEntitlement(): Boolean =
        entitlements.active.containsKey(PremiumGate.ENTITLEMENT_ID)
}
