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

package com.fulldive.wallet.presentation.base

import android.annotation.TargetApi
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.CallSuper
import androidx.viewbinding.ViewBinding
import com.fulldive.wallet.di.IEnrichableActivity
import com.fulldive.wallet.di.IInjectorHolder
import com.fulldive.wallet.extensions.or
import com.fulldive.wallet.extensions.toast
import com.joom.lightsaber.Injector
import moxy.MvpDelegate

abstract class BaseMvpFrameLayout<VB : ViewBinding> : FrameLayout,
    IInjectorHolder {

    protected val appInjector: Injector
        get() {
            return (context as? IEnrichableActivity)
                .or { (context as ContextWrapper).baseContext as IEnrichableActivity }
                .appInjector
        }

    private var isCreated: Boolean = false

    private val mvpDelegate: MvpDelegate<out BaseMvpFrameLayout<VB>> by lazy {
        MvpDelegate(this@BaseMvpFrameLayout)
    }
    protected var binding: VB? = null

    abstract fun getViewBinding(): VB

    protected fun binding(viewBinding: VB.() -> Unit) {
        binding?.apply { viewBinding() }
    }

    constructor(context: Context) : super(context) {
        init(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs, defStyleAttr)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    open fun showMessage(message: String) {
        context.toast(message)
    }

    open fun showMessage(resourceId: Int) {
        context.toast(resourceId)
    }

    @CallSuper
    protected open fun init(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
        defStyleRes: Int = 0
    ) {
        inflateLayout()
        create()
    }

    @CallSuper
    open fun inflateLayout() {
        getViewBinding().apply {
            binding = this
        }.root
    }

    @CallSuper
    open fun initLayout() {
    }

    @CallSuper
    open fun afterInitLayout() {
    }

    fun performAttach() {
        create()
    }

    override fun getInjector() = appInjector

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        create()
        mvpDelegate.onAttach()
    }

    override fun onDetachedFromWindow() {
        mvpDelegate.onDetach()
        super.onDetachedFromWindow()
    }

    fun create() {
        if (!isCreated) {
            initLayout()
            mvpDelegate.onCreate()
            afterInitLayout()
            isCreated = true
        }
    }

    @CallSuper
    open fun onDestroy() {
        mvpDelegate.onDestroyView()
        mvpDelegate.onDestroy()
        binding = null
    }
}