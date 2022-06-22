package com.cosmik.syncplay.main

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cosmik.syncplay.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    //Declaring the ViewBinding global variables :
    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Inflating the UI as per the ViewBinding method :
        _binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        //This disables crashing.
        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            Log.e(
                "Error ${Thread.currentThread().stackTrace[2]}",
                paramThrowable.localizedMessage!!
            )
            paramThrowable.printStackTrace()
        }


        /* Initializing my pager's different elements*/
        val mymainPager = binding.mainPager
        mymainPager.adapter = MainPagerAdapter(this)
        val myPagerTabs = binding.mainPagerTabs
        TabLayoutMediator(myPagerTabs, mymainPager) { tab, position ->
            tab.text =
                (mymainPager.adapter as MainPagerAdapter).mFragmentNames[position] //Sets tabs names as mentioned in ViewPagerAdapter fragmentNames array, this can be implemented in many different ways.
        }.attach()

    }

}