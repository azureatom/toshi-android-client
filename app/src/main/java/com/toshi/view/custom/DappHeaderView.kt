/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.view.custom

import android.content.Context
import android.graphics.PorterDuff
import android.support.design.widget.AppBarLayout
import android.util.AttributeSet
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import com.toshi.R
import com.toshi.extensions.getColorById
import kotlinx.android.synthetic.main.view_dapp_header.view.closeButton
import kotlinx.android.synthetic.main.view_dapp_header.view.collapsingToolbar
import kotlinx.android.synthetic.main.view_dapp_header.view.headerImage
import kotlinx.android.synthetic.main.view_dapp_header.view.headerImageWrapper
import kotlinx.android.synthetic.main.view_dapp_header.view.toolbar

class DappHeaderView : AppBarLayout {

    private var prevOffset = -1
    var offsetChangedListener: ((Float) -> Unit)? = null

    constructor(context: Context): super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        init()
    }

    private fun init() {
        inflate(context, R.layout.view_dapp_header, this)
        initListeners()
    }

    private fun initListeners() {
        addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            handleOffsetChanged(appBarLayout, verticalOffset)
        }
    }

    private fun handleOffsetChanged(appBarLayout: AppBarLayout, verticalOffset: Int) {
        if (prevOffset == verticalOffset) return
        prevOffset = verticalOffset
        val absVerticalOffset = Math.abs(verticalOffset).toFloat()
        val scrollRange = appBarLayout.totalScrollRange.toFloat()
        val percentage = 1f - (absVerticalOffset / scrollRange)
        setHeaderImageAlpha(percentage)
        offsetChangedListener?.invoke(percentage)
    }

    private fun setHeaderImageAlpha(percentage: Float) {
        headerImageWrapper.alpha = percentage
        headerImage.alpha = percentage
    }

    fun enableCollapsing() {
        setExpanded(true)
        setDarkToolbar()
    }

    fun disableCollapsing() {
        setExpanded(false)
        setLightToolbar()
        val lp = collapsingToolbar.layoutParams as AppBarLayout.LayoutParams
        lp.height = WRAP_CONTENT
    }

    private fun setLightToolbar() {
        collapsingToolbar.setBackgroundColor(getColorById(R.color.windowBackground))
        toolbar.setBackgroundColor(getColorById(R.color.windowBackground))
        toolbar.closeButton.setColorFilter(getColorById(R.color.textColorSecondary), PorterDuff.Mode.SRC_IN)
    }

    private fun setDarkToolbar() {
        collapsingToolbar.setBackgroundColor(getColorById(R.color.colorPrimary))
        toolbar.closeButton.setColorFilter(getColorById(R.color.textColorContrast), PorterDuff.Mode.SRC_IN)
    }
}