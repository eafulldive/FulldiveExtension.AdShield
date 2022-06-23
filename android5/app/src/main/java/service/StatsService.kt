/*
 * This file is part of Blokada.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright © 2022 Blocka AB. All rights reserved.
 *
 * @author Karol Gusak (karol@blocka.net)
 */

package service

import com.fulldive.wallet.di.IInjectorHolder
import com.fulldive.wallet.di.components.ApplicationComponent
import com.fulldive.wallet.extensions.withDefaults
import com.fulldive.wallet.interactors.ExperienceExchangeInterator
import com.fulldive.wallet.models.Chain
import com.joom.lightsaber.Injector
import com.joom.lightsaber.Lightsaber
import com.joom.lightsaber.getInstance
import engine.Host
import io.reactivex.disposables.Disposable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import model.*
import utils.Logger
import java.util.*

object StatsService : PrintsDebugInfo, IInjectorHolder {

    private val blockedHosts: MutableMap<Host, Long> = mutableMapOf()
    private val internalStats: MutableMap<StatsPersistedKey, StatsPersistedEntry> = mutableMapOf()
    private val persistence = PersistenceService

    private var runtimeAllowed = 0
    private var runtimeDenied = 0

    override fun getInjector(): Injector {
        return appInjector
    }

    private lateinit var appInjector: Injector
    private val experienceExchangeInterator by lazy { appInjector.getInstance<ExperienceExchangeInterator>() }

    var disposable: Disposable? = null

    fun setup() {
        appInjector = Lightsaber.Builder().build().createInjector(
            ApplicationComponent(ContextService.requireAppContext())
        )
        if (disposable?.isDisposed != false) {
            disposable = experienceExchangeInterator
                .observeIfExperienceExchangeAvailable(Chain.fdCoinDenom)
                .withDefaults()
                .subscribe()
        }
        internalStats.clear()
        internalStats.putAll(persistence.load(StatsPersisted::class).entries)
        LogService.onShareLog("Stats", this)
    }

    fun clear() {
        internalStats.clear()
        persistence.save(StatsPersisted(internalStats))
        disposable?.dispose()
        disposable = null
    }

    suspend fun passedAllowed(host: Host) {
        increment(host, HistoryEntryType.passed_allowed)
        runtimeAllowed += 1
    }

    suspend fun blockedDenied(host: Host) {
        increment(host, HistoryEntryType.blocked_denied)
        runtimeDenied += 1
    }

    suspend fun blocked(host: Host) {
        if (needIncrement(host)) {
            increment(host, HistoryEntryType.blocked)
            incrementXP()
            runtimeDenied += 1
        }
    }

    suspend fun passed(host: Host) {
        increment(host, HistoryEntryType.passed)
        runtimeAllowed += 1
    }

    suspend fun getStats(): Stats {
        return coroutineScope {
            Stats(
                allowed = runtimeAllowed,
                denied = runtimeDenied,
                entries = internalStats.map {
                    HistoryEntry(
                        name = it.key.host(),
                        type = it.key.type(),
                        time = Date(it.value.lastEncounter),
                        requests = it.value.occurrences
                    )
                }
            )
        }
    }

    private suspend fun increment(host: Host, type: HistoryEntryType) {
        coroutineScope {
            val key = statsPersistedKey(host, type)
            val entry = internalStats.getOrElse(key) {
                StatsPersistedEntry(lastEncounter = System.currentTimeMillis(), occurrences = 0)
            }
            entry.lastEncounter = System.currentTimeMillis()
            entry.occurrences += 1
            internalStats[key] = entry

            // XXX: we create a wrapper object on every time we persist
            persistence.save(StatsPersisted(internalStats))

            launch {
                // XXX: not the best place, but we want realtime notification updates
                if (!AppSettingsService.getIsBlockHistoryAtNotification()) {
                    MonitorService.setStats(getStats())
                }
            }
        }
    }

    private fun incrementXP() {
        experienceExchangeInterator.setExperience(1)
    }

    private fun needIncrement(host: Host): Boolean {
        val hostLastCheckTime = blockedHosts[host]
        val now = System.currentTimeMillis()

        return when {
            hostLastCheckTime == null
                    || now - hostLastCheckTime >= 5000L -> {
                blockedHosts[host] = now
                true
            }
            else -> {
                false
            }
        }
    }

    override fun printDebugInfo() {
        Logger.v("Stats", internalStats.keys.toString())
    }

}

// XXX: a proper solution would be to configure Moshi to handle Object as a key
private typealias StatsPersistedKey = String

private fun String.host(): Host = this.substringBeforeLast(";")
private fun String.type(): HistoryEntryType = HistoryEntryType.valueOf(this.substringAfterLast(";"))
private fun statsPersistedKey(host: Host, type: HistoryEntryType) = "$host;$type"