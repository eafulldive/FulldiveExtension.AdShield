/*
 * Copyright (c) 2022 FullDive
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.fulldive.wallet.presentation.exchange

import com.fulldive.wallet.di.modules.DefaultModule
import com.fulldive.wallet.extensions.withDefaults
import com.fulldive.wallet.interactors.ExperienceExchangeInterator
import com.fulldive.wallet.interactors.SettingsLocalDataSource
import com.fulldive.wallet.interactors.WalletInteractor
import com.fulldive.wallet.models.ExchangeValue
import com.fulldive.wallet.presentation.base.BaseMoxyPresenter
import com.joom.lightsaber.ProvidedBy
import moxy.InjectViewState
import service.AppSettingsService
import javax.inject.Inject

@InjectViewState
@ProvidedBy(DefaultModule::class)
class ExchangePresenter @Inject constructor(
    private val walletInteractor: WalletInteractor,
    private val experienceExchangeInterator: ExperienceExchangeInterator
) : BaseMoxyPresenter<ExchangeView>() {

    private var userExperience = 0
    private var exchangeCurrency = 0

    override fun onFirstViewAttach() {
        super.onFirstViewAttach()
        experienceExchangeInterator
            .observeExchangePacks()
            .withDefaults()
            .compositeSubscribe(
                onNext = { packs ->
                    packs.firstOrNull { pack ->
                        pack.exchangeValues.map { it.type }.contains(ExchangeValue.FD_TOKEN)
                    }?.let {
                        exchangeCurrency = it.amount //todo
                    }
                }
            )

        experienceExchangeInterator
            .getExperience()
            .withDefaults()
            .compositeSubscribe(
                onSuccess = { experience ->
                    userExperience = experience
                    viewState.showUserExperience(
                        experience = experience,
                        minimumExchangeExperience = SettingsLocalDataSource.EXPERIENCE_MIN_EXCHANGE_COUNT,
                        coins = userExperience / exchangeCurrency.toDouble()
                    )
                }
            )
    }

    fun validateExperience(experienceString: String) {
        val experience = if (experienceString.isEmpty()) 0 else experienceString.toInt()
        val isValid = userExperience >= SettingsLocalDataSource.EXPERIENCE_MIN_EXCHANGE_COUNT &&
                experience in 1..userExperience

        viewState.experienceIsValid(isValid)
        viewState.showAvailableFulldiveCoins(experience / exchangeCurrency.toDouble())
    }

    fun exchangeExperience(title: String, address: String) {
        experienceExchangeInterator
            .exchangeExperience(title, address)
            .withDefaults()
            .compositeSubscribe(
                onSuccess = {

                }
            )
    }
}