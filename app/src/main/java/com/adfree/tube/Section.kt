package com.adfree.tube

import android.view.View

interface Section {
    val view: View
    fun onBack(): Boolean = false
    fun onShow() {}
    fun onHide() {}
    fun onDestroy() {}
}
