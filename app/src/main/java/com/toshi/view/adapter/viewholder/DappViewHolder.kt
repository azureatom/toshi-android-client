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

package com.toshi.view.adapter.viewholder

import android.support.v7.widget.RecyclerView
import android.view.View
import android.widget.ImageView
import com.toshi.R
import com.toshi.model.network.dapp.Dapp
import com.toshi.util.ImageUtil
import kotlinx.android.synthetic.main.list_item__dapp.view.description
import kotlinx.android.synthetic.main.list_item__dapp.view.image
import kotlinx.android.synthetic.main.list_item__dapp.view.name

class DappViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    fun setDapp(dapp: Dapp): DappViewHolder {
        itemView.name.text = dapp.name
        itemView.description.text = dapp.description
        loadImage(itemView.image, dapp.icon)
        return this
    }

    private fun loadImage(imageView: ImageView, icon: String?) {
        if (icon != null) ImageUtil.load(icon, imageView)
        else imageView.setImageResource(R.drawable.placeholder)
    }

    fun setOnClickListener(dapp: Dapp, listener: (Dapp) -> Unit): DappViewHolder {
        itemView.setOnClickListener { listener(dapp) }
        return this
    }
}