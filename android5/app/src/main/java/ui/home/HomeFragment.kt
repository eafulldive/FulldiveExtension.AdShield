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

package ui.home

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import appextension.getColorCompat
import appextension.getDrawableCompat
import com.fulldive.wallet.presentation.main.MainCryptoFragment
import kotlinx.coroutines.delay
import model.*
import org.adshield.R
import service.AlertDialogService
import service.EnvironmentService
import service.UpdateService
import ui.*
import ui.settings.SettingsFragmentDirections
import ui.utils.getColorFromAttr
import utils.Links
import utils.withBoldSections

class HomeFragment : Fragment() {

    private val alert = AlertDialogService
    private lateinit var vm: TunnelViewModel
    private lateinit var accountVM: AccountViewModel
    private lateinit var adsCounterVm: AdsCounterViewModel
    private lateinit var appSettingsVm: AppSettingsViewModel

    private lateinit var activateView: ActivateView
    private lateinit var statusTextView: TextView
    private lateinit var longStatusTextView: TextView
    private lateinit var idoAnnouncementLayout: FrameLayout

    private val colorConnected by lazy { requireContext().getColorCompat(R.color.colorAccent) }
    private val colorDisconnected by lazy { requireContext().getColorCompat(R.color.colorIconPrimary) }
    private val backgroundConnected by lazy { requireContext().getDrawableCompat(R.drawable.background_button_connected) }
    private val backgroundDisconnected by lazy { requireContext().getDrawableCompat(R.drawable.background_button_disconnected) }

    private fun setStatusConnected() {
        statusTextView.setTextColor(colorConnected)
        statusTextView.background = backgroundConnected
        statusTextView.text = getString(R.string.status_connected)
    }

    private fun setStatusDisconnected() {
        statusTextView.setTextColor(colorDisconnected)
        statusTextView.background = backgroundDisconnected
        statusTextView.text = getString(R.string.status_disconnected)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        iniViewModel()
        val root = inflater.inflate(R.layout.fragment_home, container, false)
        initViews(root)

        //TODO: MOCKED
        idoAnnouncementLayout.setOnClickListener {
            appSettingsVm.setIdoAnnouncementClicked()
            findNavController()
                .apply {
                    navigate(R.id.navigation_home)
                    navigate(
                        HomeFragmentDirections.actionNavigationHomeToWebFragment(
                            Links.community,
                            "MOCKED"
                        )
                    )
                }
        }

        val updateLongStatus = { status: TunnelStatus, counter: Long? ->
            when {
                status.inProgress -> longStatusTextView.text =
                    getString(R.string.home_status_detail_progress)
                status.active && status.gatewayId != null && counter == null -> {
                    setStatusConnected()
                    longStatusTextView.text = (
                            "${getString(R.string.home_status_detail_active)}\n${getString(R.string.home_status_detail_plus)}"
                            ).withBoldSections(requireContext().getColorFromAttr(R.attr.colorAccent))
                }
                status.active && EnvironmentService.isSlim() -> {
                    setStatusConnected()
                    longStatusTextView.text = getString(R.string.home_status_detail_active_slim)
                }
                status.active && counter == null -> {
                    setStatusConnected()
                    longStatusTextView.text = getString(R.string.home_status_detail_active)
                        .withBoldSections(requireContext().getColorFromAttr(R.attr.colorAccent))
                }
                status.active && status.gatewayId != null -> {
                    setStatusConnected()
                    val statusString = "${
                        getString(
                            R.string.home_status_detail_active_with_counter,
                            counter.toString()
                        )
                    }\n ${getString(R.string.home_status_detail_plus)}"
                    longStatusTextView.text =
                        statusString.withBoldSections(requireContext().getColorFromAttr(R.attr.colorAccent))
                }
                status.active -> {
                    setStatusConnected()
                    longStatusTextView.text = getString(
                        R.string.home_status_detail_active_with_counter,
                        counter.toString()
                    )
                        .withBoldSections(requireContext().getColorFromAttr(R.attr.colorAccent))
                }
                else -> {
                    setStatusDisconnected()
                    statusTextView.text = getString(R.string.status_disconnected)
                    longStatusTextView.text = getString(R.string.home_action_tap_to_activate)
                }
            }

            longStatusTextView.setOnClickListener {
                parentFragmentManager
                    .beginTransaction()
                    .replace(R.id.nav_host_fragment, MainCryptoFragment(), "main")
                    .addToBackStack("main")
                    .commit()

//                // todo
//                when {
//                    status.inProgress -> Unit
//                    status.error != null -> Unit
//                    status.active -> {
//                        val fragment = ProtectionLevelFragment.newInstance()
//                        fragment.show(parentFragmentManager, null)
//                    }
//                    else -> vm.turnOn()
//                }
            }
        }

        vm.tunnelStatus.observe(viewLifecycleOwner) { status ->
            activateView.inactiveMode = !status.inProgress && !status.active
            activateView.activeMode = status.active
            activateView.isEnabled = !status.inProgress

            activateView.setOnClickListener {
                activateView.onClicked()
                when {
                    status.inProgress -> Unit
                    status.error != null -> Unit
                    status.active -> {
                        vm.turnOff()
                        adsCounterVm.roll()
                    }
                    else -> vm.turnOn()
                }
            }

            when {
                status.inProgress -> statusTextView.text = getString(R.string.connecting_title)
                status.active -> setStatusConnected()
                else -> setStatusDisconnected()
            }

            updateLongStatus(status, adsCounterVm.counter.value?.let {
                if (it == 0L) null else it
            })

            when (status.error) {
                null -> Unit
                is NoPermissions -> showVpnPermsSheet()
                else -> showFailureDialog(status.error)
            }
        }

        adsCounterVm.counter.observe(viewLifecycleOwner) { counter ->
            vm.tunnelStatus.value?.let { s ->
                updateLongStatus(s, counter)
            }
        }

        accountVM.account.observe(viewLifecycleOwner) { account ->
            if (!account.isActive()) {
                setHasOptionsMenu(true)
            }
        }

        appSettingsVm.isIdoAnnouncementClicked.observe(viewLifecycleOwner) { isIdoAnnouncementClicked ->
            idoAnnouncementLayout.isVisible = false//TODO MOCKED!isIdoAnnouncementClicked
        }

        lifecycleScope.launchWhenCreated {
            delay(1000)
            UpdateService.handleUpdateFlow(
                onOpenDonate = {
                    findNavController()
                        .apply {
                            navigate(R.id.navigation_home)
                            navigate(
                                HomeFragmentDirections.actionNavigationHomeToWebFragment(
                                    Links.donate, getString(R.string.universal_action_donate)
                                )
                            )
                        }
                },
                onOpenMore = {
                    // Display the thank you page
                    findNavController()
                        .apply {
                            navigate(R.id.navigation_home)
                            navigate(
                                HomeFragmentDirections.actionNavigationHomeToWebFragment(
                                    Links.updated, getString(R.string.update_label_updated)
                                )
                            )
                        }
                }
            )
        }
        return root
    }

    private fun showVpnPermsSheet() {
        val fragment = AskVpnProfileFragment.newInstance()
        fragment.show(parentFragmentManager, null)
    }

    private fun showLocationSheet() {
        val fragment = LocationFragment.newInstance()
        fragment.show(parentFragmentManager, null)
    }

    private fun showPlusSheet() {
        val fragment = PaymentFragment.newInstance()
        fragment.show(parentFragmentManager, null)
    }

    private fun showFailureDialog(ex: BlokadaException) {
        val additional: Pair<String, () -> Unit>? =
            if (shouldShowKbLink(ex)) getString(R.string.universal_action_learn_more) to {
                findNavController()
                    .apply {
                        navigate(
                            HomeFragmentDirections.actionNavigationHomeToWebFragment(
                                Links.tunnelFailure, getString(R.string.universal_action_learn_more)
                            )
                        )
                    }
            }
            else null

        alert.showAlert(
            message = mapErrorToUserFriendly(ex),
            onDismiss = vm::setInformedUserAboutError,
            additionalAction = additional
        )
    }

    private fun iniViewModel() {
        activity?.let {
            vm = ViewModelProvider(it.app()).get(TunnelViewModel::class.java)
            accountVM = ViewModelProvider(it.app()).get(AccountViewModel::class.java)
            adsCounterVm = ViewModelProvider(it.app()).get(AdsCounterViewModel::class.java)
            appSettingsVm = ViewModelProvider(it.app()).get(AppSettingsViewModel::class.java)
        }
    }

    private fun initViews(root: View) {
        activateView = root.findViewById(R.id.activateButton)
        statusTextView = root.findViewById(R.id.statusTextView)
        idoAnnouncementLayout = root.findViewById(R.id.idoAnnouncementLayout)
        longStatusTextView = root.findViewById(R.id.statusDescriptionTextView)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.home_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.home_donate -> {
                findNavController()
                    .apply {
                        navigate(R.id.navigation_settings)
                        navigate(
                            SettingsFragmentDirections.actionNavigationSettingsToWebFragment(
                                Links.donate, getString(R.string.universal_action_donate)
                            )
                        )
                    }

                true
            }
            else -> false
        }
    }
}