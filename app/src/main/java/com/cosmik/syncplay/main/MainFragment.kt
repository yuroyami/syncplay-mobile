package com.cosmik.syncplay.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.cosmik.syncplay.databinding.MainFragmentBinding
import com.google.android.material.tabs.TabLayoutMediator


class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private var _binding: MainFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = MainFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MainFragmentBinding.bind(view)


        /* Initializing my pager's different elements*/
        val mymainPager = binding.mainPager
        mymainPager.adapter = MainPagerAdapter(requireActivity())
        val myPagerTabs = binding.mainPagerTabs
        TabLayoutMediator(myPagerTabs, mymainPager) { tab, position ->
            tab.text =
                (mymainPager.adapter as MainPagerAdapter).mFragmentNames[position] //Sets tabs names as mentioned in ViewPagerAdapter fragmentNames array, this can be implemented in many different ways.
        }.attach()
    }

}