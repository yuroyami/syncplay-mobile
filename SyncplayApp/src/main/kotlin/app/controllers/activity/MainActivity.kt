package app.controllers.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.controllers.adapters.MainPagerAdapter
import app.databinding.ActivityMainBinding
import com.google.android.material.tabs.TabLayoutMediator

class MainActivity : AppCompatActivity() {

    //Declaring the ViewBinding global variables :
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Inflating the UI as per the ViewBinding method :
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        /* Putting together the components of our ViewPager */
        binding.mainPager.adapter = MainPagerAdapter(this)
        TabLayoutMediator(binding.mainPagerTabs, binding.mainPager) { tab, position ->
            tab.text = (binding.mainPager.adapter as MainPagerAdapter).mFragmentNames[position]
        }.attach()
    }
}