/*
 * Adapted from Mika (https://github.com/wangzan101/Mika)
 * Copyright 2026 wangzan101 — Licensed under the Apache License, Version 2.0.
 * Modifications for Mibot (com.dl1991.mibot), distributed under GPL-3.0-or-later:
 * no USB start script and no automatic toggling of Wireless Debugging.
 */
package com.openminis.app.mibot.phone

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.Settings
import com.openminis.app.mibot.phone.adb.AdbConnectPort
import com.openminis.app.mibot.phone.adb.AdbConnection
import com.openminis.app.mibot.phone.adb.AdbPairing
import com.openminis.app.mibot.phone.adb.AdbServiceTypes
import com.openminis.app.mibot.phone.adb.AdbTls
import com.openminis.app.mibot.phone.adb.ConnectCandidates
import com.openminis.app.mibot.phone.adb.LocalPortScan
import com.openminis.app.mibot.phone.adb.NioPortScanner
import com.openminis.app.mibot.phone.adb.PortCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.Inet4Address
import java.util.concurrent.atomic.AtomicReference

object DeveloperSettings {
    const val ADB_WIFI_ENABLED = "adb_wifi_enabled"
    private const val WIRELESS_DEBUGGING_KEY = "toggle_adb_wireless"
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"

    val wirelessDebuggingUri: android.net.Uri = Settings.Global.getUriFor(ADB_WIFI_ENABLED)

    fun developerOptions(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1

    fun wirelessDebugging(context: Context): Boolean =
        Settings.Global.getInt(context.contentResolver, ADB_WIFI_ENABLED, 0) == 1

    /** Developer options with the "Wireless debugging" row highlighted (AOSP Settings understands this). */
    fun wirelessDebuggingPage(): List<android.content.Intent> {
        val highlight = android.content.Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_KEY)
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, android.os.Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, WIRELESS_DEBUGGING_KEY) })
        return listOf(highlight, android.content.Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS), android.content.Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
    }
}

internal fun ownCertPin(context: Context): String {
    val signers = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        .signingInfo?.apkContentsSigners?.map { it.toByteArray() }
    return HelperHandoff.certPin(signers) ?: error("cannot read own signing certificate")
}

/**
 * The built-in helper: pairing with the phone's own Wireless Debugging (one-time
 * 6-digit code, no PC), launching the helper through the local adbd, and a
 * watchdog that reconnects when Wi-Fi / Wireless Debugging come back.
 */
class BuiltinShellManager(context: Context, private val hub: HelperHub) {
    private val app = context.applicationContext
    private var started = false

    private val _scanningPorts = MutableStateFlow(false)
    val scanningPorts: StateFlow<Boolean> = _scanningPorts.asStateFlow()

    private val platform: AndroidPlatform by lazy {
        AndroidPlatform(app, hub, { lastConnectCandidates = it }, { _scanningPorts.value = it })
    }

    @Volatile var lastConnectCandidates: ConnectCandidates? = null
        private set

    val engine: BuiltinShellEngine by lazy {
        BuiltinShellEngine(platform, hub.shell, CoroutineScope(SupervisorJob() + Dispatchers.Default), Dispatchers.IO)
    }

    fun need() = engine.need()

    suspend fun connectWithPort(port: Int) {
        platform.manualPort.set(port)
        engine.retry()
    }

    @Synchronized
    fun start() {
        if (started || Build.VERSION.SDK_INT < MIN_SDK) return
        started = true
        val e = engine
        app.contentResolver.registerContentObserver(
            DeveloperSettings.wirelessDebuggingUri, false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = e.wirelessDebuggingChanged()
            },
        )
        runCatching {
            app.getSystemService(ConnectivityManager::class.java).registerNetworkCallback(
                NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) = e.wifiAvailable()
                },
            )
        }
        e.start()
    }

    private class AndroidPlatform(
        private val app: Context,
        private val hub: HelperHub,
        private val candidates: (ConnectCandidates) -> Unit,
        private val scanning: (Boolean) -> Unit,
    ) : BuiltinShellPlatform {
        val manualPort = AtomicReference<Int?>(null)
        private val scan = LocalPortScan(NioPortScanner(), ::localHosts, { LocalPortScan.parseRange(portRangeText()) })
        private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private val finder = NsdAdbServiceFinder(app)
        private val portCache = object : PortCache {
            override fun load() = prefs.getInt("port", 0).takeIf { it > 0 }
            override fun save(port: Int) { prefs.edit().putInt("port", port).apply() }
        }

        override fun paired() = Build.VERSION.SDK_INT >= MIN_SDK && prefs.getBoolean("paired", false)
        override fun setPaired(paired: Boolean) { prefs.edit().putBoolean("paired", paired).apply() }
        override fun usbStarted() = false
        override fun setUsbStarted(on: Boolean) {}

        override fun wirelessDebugging(): Boolean? =
            Settings.Global.getString(app.contentResolver, DeveloperSettings.ADB_WIFI_ENABLED)?.let { it == "1" }

        override fun wifiConnected(): Boolean = runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
        }.getOrDefault(true)

        override suspend fun findPairingPort(timeoutMs: Long): Int? = finder.findPorts(AdbServiceTypes.PAIRING, timeoutMs).firstOrNull()

        override fun pair(port: Int, code: String) {
            AdbPairing(AndroidAdb.tls(AndroidAdb.identity(app))).pair(LOCALHOST, port, code)
        }

        override suspend fun launchHelper() {
            val tls = AndroidAdb.tls(AndroidAdb.identity(app))
            val command = HelperHandoff.command(app.applicationInfo.sourceDir, app.packageName, Process.myUid(), ownCertPin(app))
            val connection = connect(tls)
            val before = hub.shell.value
            hub.source = "adb"
            connection.use {
                val stream = it.exec(command)
                stream.write(HelperHandoff.stdinLine(hub.issueLaunchNonce()))
                withTimeoutOrNull(HANDOFF_WAIT_MS) { hub.shell.first { s -> s != null && s !== before } }
            }
        }

        suspend fun connect(tls: AdbTls = AndroidAdb.tls(AndroidAdb.identity(app))): AdbConnection =
            AdbConnectPort(
                ::tlsPort, finder, portCache, report = candidates, scan = scan, manual = { manualPort.getAndSet(null) }, scanning = scanning,
            ).connect { host, port -> AdbConnection.open(host, port, tls) }

        private fun localHosts(): List<String> = listOf(LOCALHOST) + runCatching {
            val cm = app.getSystemService(ConnectivityManager::class.java)
            @Suppress("DEPRECATION")
            cm.allNetworks.filter { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true }
                .flatMap { cm.getLinkProperties(it)?.linkAddresses.orEmpty() }
                .mapNotNull { (it.address as? Inet4Address)?.hostAddress }
        }.getOrDefault(emptyList())

        private fun portRangeText(): String? = runCatching { File(PORT_RANGE_FILE).readText() }.getOrNull()

        override fun stopHelper() {
            runCatching { hub.shell.value?.destroy() }
        }

        override fun forget() {
            hub.forget()
            AndroidAdb.forget(app)
            prefs.edit().remove("port").apply()
        }

        @SuppressLint("PrivateApi")
        private fun tlsPort(): Int? = runCatching {
            Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
                .invoke(null, TLS_PORT_PROPERTY) as String?
        }.getOrNull()?.trim()?.toIntOrNull()
    }

    companion object {
        /** App control on the hidden display is supported from Android 12. */
        const val MIN_SDK = Build.VERSION_CODES.S
        private const val PREFS = "mibot_builtin_shell"
        private const val TLS_PORT_PROPERTY = "service.adb.tls.port"
        private const val LOCALHOST = AdbConnectPort.LOCALHOST
        private const val PORT_RANGE_FILE = "/proc/sys/net/ipv4/ip_local_port_range"
        private const val HANDOFF_WAIT_MS = 15_000L
    }
}
