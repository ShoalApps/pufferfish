package io.github.shoalapps.pufferfish.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import io.github.shoalapps.pufferfish.Graph
import kotlinx.coroutines.flow.MutableStateFlow

/** Connectivity and Data Saver policy shared by discovery, radio and downloads. */
object OfflineMode {
    val online = MutableStateFlow(false)
    private val cm get() = Graph.app.getSystemService(ConnectivityManager::class.java)
    fun available(): Boolean = !Graph.store.settings.value.offlineMode &&
        cm?.getNetworkCapabilities(cm?.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    fun savingData() = cm?.isActiveNetworkMetered != false &&
        cm?.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
    fun backgroundAllowed() = available() && !savingData()
    fun downloadsAllowed() = backgroundAllowed() &&
        (!Graph.store.settings.value.downloadWifiOnly || cm?.isActiveNetworkMetered == false)
    fun changed() {
        online.value = available()
        io.github.shoalapps.pufferfish.player.Downloads.policyChanged()
    }
    fun init() {
        online.value = available()
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = changed()
            override fun onLost(network: Network) = changed()
        })
        androidx.core.content.ContextCompat.registerReceiver(Graph.app, object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) = changed()
        }, android.content.IntentFilter(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
    }
}
