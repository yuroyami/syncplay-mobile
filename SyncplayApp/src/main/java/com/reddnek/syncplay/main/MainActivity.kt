package com.chromaticnoob.syncplay.main

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.chromaticnoob.syncplay.databinding.ActivityMainBinding
import com.chromaticnoob.syncplayutils.SyncplayUtils
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

        /* Enabling IMMERSIVE MODE again */
        SyncplayUtils.showSystemUI(window)

        /* Setting the Status Bar color */
        SyncplayUtils.setStatusBarColor(Color.parseColor("#7A7A7A"), window)

        /* Disabling cut-out mode for main screen */
        SyncplayUtils.cutoutMode(false, window)

        /* Putting together the components of our ViewPager */
        val mymainPager = binding.mainPager
        mymainPager.adapter = MainPagerAdapter(this)
        val myPagerTabs = binding.mainPagerTabs
        TabLayoutMediator(myPagerTabs, mymainPager) { tab, position ->
            tab.text = (mymainPager.adapter as MainPagerAdapter).mFragmentNames[position]
        }.attach()


        //This disables crashing.
//        Thread.setDefaultUncaughtExceptionHandler { _, paramThrowable ->
//            Log.e(
//                "Error ${Thread.currentThread().stackTrace[2]}",
//                paramThrowable.localizedMessage!!
//            )
//            paramThrowable.printStackTrace()
//        }
    }
}