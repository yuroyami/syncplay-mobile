package com.reddnek.syncplay.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.tabs.TabLayoutMediator
import com.reddnek.syncplay.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    //Declaring the ViewBinding global variables :
    private lateinit var binding: ActivityMainBinding


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Inflating the UI as per the ViewBinding method :
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        /* Putting together the components of our ViewPager */
        val mymainPager = binding.mainPager
        mymainPager.adapter = MainPagerAdapter(this)
        val myPagerTabs = binding.mainPagerTabs
        TabLayoutMediator(myPagerTabs, mymainPager) { tab, position ->
            tab.text = (mymainPager.adapter as MainPagerAdapter).mFragmentNames[position]
        }.attach()

    }
}