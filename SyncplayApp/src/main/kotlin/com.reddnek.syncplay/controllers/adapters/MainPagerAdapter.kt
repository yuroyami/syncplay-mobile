package com.reddnek.syncplay.controllers.adapters

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.reddnek.syncplay.R
import com.reddnek.syncplay.controllers.fragment.AboutFragment
import com.reddnek.syncplay.controllers.fragment.ConnectFragment
import com.reddnek.syncplay.controllers.fragment.SettingsFragment

class MainPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {

    private val mFragments = arrayOf(ConnectFragment(), SettingsFragment(), AboutFragment())
    val mFragmentNames = arrayOf(
        fa.baseContext.getString(R.string.tab_connect),
        fa.baseContext.getString(R.string.tab_settings),
        fa.baseContext.getString(R.string.tab_about)
    )

    override fun getItemCount(): Int {
        return mFragments.size
    }

    override fun createFragment(position: Int): Fragment {
        return mFragments[position]
    }
}