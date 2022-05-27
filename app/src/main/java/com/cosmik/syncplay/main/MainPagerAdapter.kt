package com.cosmik.syncplay.main

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cosmik.syncplay.about.AboutFragment


class MainPagerAdapter(fa: FragmentActivity?) : FragmentStateAdapter(fa!!) {
    private val mFragments = arrayOf<Fragment>( //Initialize fragments views
        //Fragment views are initialized like any other fragment (Extending Fragment)
        ConnectFragment(),  //First fragment to be displayed within the pager tab number 1
        SettingsFragment(),
        AboutFragment()
    )
    val mFragmentNames = arrayOf( //Tabs names array
        "Connect",
        "Settings",
        "About"
    )


    override fun getItemCount(): Int {
        return mFragments.size //Number of fragments displayed
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(position)
    }

    override fun createFragment(position: Int): Fragment {
        return mFragments[position]
    }
}