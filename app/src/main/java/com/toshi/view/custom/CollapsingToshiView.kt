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

import android.annotation.TargetApi
import android.content.Context
import android.os.Build
import android.support.design.widget.AppBarLayout
import android.support.v4.graphics.ColorUtils
import android.util.AttributeSet
import android.widget.LinearLayout
import com.toshi.R
import com.toshi.extensions.addPadding
import com.toshi.extensions.getColorById
import com.toshi.extensions.getPxSize
import com.toshi.extensions.isVisible
import com.toshi.view.adapter.listeners.TextChangedListener
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.closeButton
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.input
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.inputWrapper
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.toshiIcon
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.toshiText
import kotlinx.android.synthetic.main.view_collapsing_toshi.view.wrapper

class CollapsingToshiView : AppBarLayout {

    private val backgroundColor by lazy { getColorById(R.color.colorPrimary) }
    private val inputElevation by lazy { getPxSize(R.dimen.dappsInputElevation) }
    private val inputZ by lazy { getPxSize(R.dimen.dappsInputZ) }
    private val inputMargin by lazy { getPxSize(R.dimen.dappsInputMargin) }
    private var prevOffset = -1

    var onTextChangedListener: ((String) -> Unit)? = null
    var onHeaderCollapsed: (() -> Unit)? = null
    var onHeaderExpanded: (() -> Unit)? = null

    constructor(context: Context): super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?): super(context, attrs) {
        init()
    }

    private fun init() {
        inflate(context, R.layout.view_collapsing_toshi, this)
        initListeners()
    }

    private fun initListeners() {
        closeButton.setOnClickListener { expandClearInputAndHideCloseButton() }
        input.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) collapse() }
        input.setOnClickListener { if (it.hasFocus()) collapse() }
        addOnOffsetChangedListener { appBarLayout, verticalOffset ->
            updateView(verticalOffset, appBarLayout)
        }
        input.addTextChangedListener(object : TextChangedListener() {
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                onTextChangedListener?.invoke(s.toString())
            }
        })
    }

    private fun expandClearInputAndHideCloseButton() {
        setExpanded(true, true)
        input.setText("")
        closeButton.isVisible(false)
        input.addPadding(left = input.paddingRight, right = input.paddingRight)
        onHeaderExpanded?.invoke()
    }

    private fun collapse() {
        collapseAndShowCloseButton()
        onHeaderCollapsed?.invoke()
    }

    private fun collapseAndShowCloseButton() {
        setExpanded(false, true)
        closeButton.isVisible(true)
        input.addPadding(left = 0, right = input.paddingRight)
    }

    private fun updateView(verticalOffset: Int, appBarLayout: AppBarLayout) {
        if (prevOffset == verticalOffset) return
        prevOffset = verticalOffset
        val absVerticalOffset = Math.abs(verticalOffset).toFloat()
        val scrollRange = appBarLayout.totalScrollRange.toFloat()
        if (absVerticalOffset > scrollRange) { // Prevent overscrolling.
            setExpanded(false, false)
            return
        }
        val percentage = absVerticalOffset / scrollRange
        updateOpacity(percentage)
        updateInpuWrappertMargins(percentage)
        updateBackgroundColorOpacity(percentage)
        updateElevation(percentage)
    }

    private fun updateOpacity(percentage: Float) {
        val scale = 2.3f
        val newAlpha = 1 - (percentage * scale)
        toshiIcon.alpha = newAlpha
        toshiText.alpha = newAlpha
    }

    private fun updateInpuWrappertMargins(percentage: Float) {
        val lp = inputWrapper.layoutParams as LinearLayout.LayoutParams
        val newMargin = (inputMargin * (1 - percentage)).toInt()
        lp.leftMargin = newMargin
        lp.rightMargin = newMargin
        lp.bottomMargin = newMargin
        inputWrapper.layoutParams = lp
    }

    private fun updateBackgroundColorOpacity(percentage: Float) {
        val alpha = ((1 - percentage) * 255).toInt()
        val safeAlpha = if (alpha > 255) 255 else if (alpha < 0) 0 else alpha
        val color = ColorUtils.setAlphaComponent(backgroundColor, safeAlpha)
        wrapper.setBackgroundColor(color)
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private fun updateElevation(percentage: Float) {
        val newElevation = (inputElevation * (1 - percentage))
        val newZ = (inputZ * (1 - percentage))
        inputWrapper.elevation = newElevation
        inputWrapper.translationZ = newZ
    }
}