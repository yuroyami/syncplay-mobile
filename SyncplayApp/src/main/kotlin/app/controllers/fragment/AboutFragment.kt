package app.controllers.fragment

import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.databinding.FragmentAboutBinding
import kotlin.math.roundToInt

class AboutFragment : Fragment() {

    private lateinit var binding: FragmentAboutBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = FragmentAboutBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.aboutTab1.parentLayout.setOnClickListener { binding.aboutTab1.toggleLayout() }
        binding.aboutTab2.parentLayout.setOnClickListener { binding.aboutTab2.toggleLayout() }
        binding.aboutTab3.parentLayout.setOnClickListener { binding.aboutTab3.toggleLayout() }

        val fl = binding.fillableLoader
        fl.setSvgPath(logo)
        //Getting the device's screen width
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        val usableWidth = (displayMetrics.widthPixels.toDouble() * 0.35).roundToInt()
        fl.layoutParams.width = usableWidth
        fl.layoutParams.height = usableWidth
        fl.start()

    }

    private val logo: String = "M 210.00,135.00\n" +
            "           C 220.89,133.03 231.78,125.23 240.00,118.13\n" +
            "             267.24,94.60 278.00,56.94 278.00,22.00\n" +
            "             259.11,22.00 240.51,24.50 222.00,28.20\n" +
            "             139.45,44.71 44.02,94.32 25.80,184.00\n" +
            "             25.80,184.00 22.00,211.00 22.00,211.00\n" +
            "             21.89,221.06 22.23,227.57 26.52,237.00\n" +
            "             35.24,256.19 57.92,265.69 77.00,270.85\n" +
            "             91.03,274.64 116.50,277.98 131.00,278.00\n" +
            "             131.00,278.00 153.00,278.00 153.00,278.00\n" +
            "             177.80,277.96 206.10,269.87 227.00,256.56\n" +
            "             244.42,245.48 258.65,229.21 266.40,210.00\n" +
            "             268.25,205.41 271.99,192.71 268.98,188.33\n" +
            "             265.76,183.66 259.53,191.06 257.00,193.28\n" +
            "             245.02,203.76 234.72,210.62 219.00,214.35\n" +
            "             216.35,214.97 212.53,215.45 210.30,216.93\n" +
            "             210.30,216.93 195.00,232.91 195.00,232.91\n" +
            "             188.53,238.91 180.25,243.80 172.00,246.94\n" +
            "             133.15,261.68 89.77,243.09 73.15,205.00\n" +
            "             69.60,196.84 67.11,186.93 67.00,178.00\n" +
            "             66.66,148.32 80.97,122.72 107.00,107.87\n" +
            "             136.35,91.13 173.99,96.56 198.00,120.01\n" +
            "             203.13,125.03 206.68,128.55 210.00,135.00 Z\n" +
            "           M 219.00,197.00\n" +
            "           C 230.82,194.77 240.16,190.30 248.91,181.83\n" +
            "             263.05,168.15 274.77,139.75 275.00,120.00\n" +
            "             275.03,117.10 275.64,109.63 272.57,108.17\n" +
            "             269.04,106.48 263.24,114.69 260.99,117.00\n" +
            "             251.53,126.69 245.40,132.24 234.00,139.66\n" +
            "             231.26,141.44 220.06,147.01 219.03,148.63\n" +
            "             217.76,150.65 220.80,161.74 221.27,165.00\n" +
            "             223.19,178.40 221.57,184.22 219.00,197.00 Z\n" +
            "           M 140.00,112.42\n" +
            "           C 119.16,115.57 103.00,122.42 91.09,141.00\n" +
            "             64.58,182.34 93.78,236.87 143.00,237.99\n" +
            "             151.66,238.18 162.04,235.98 170.00,232.57\n" +
            "             177.27,229.46 184.43,224.63 189.96,218.99\n" +
            "             222.71,185.53 208.49,129.86 164.00,115.36\n" +
            "             157.25,113.16 147.03,111.57 140.00,112.42 Z\n" +
            "           M 119.06,136.55\n" +
            "           C 121.44,136.11 122.53,135.84 125.00,136.55\n" +
            "             128.48,137.43 142.90,146.54 147.00,149.00\n" +
            "             147.00,149.00 173.00,164.80 173.00,164.80\n" +
            "             176.30,166.78 181.79,169.43 183.26,173.09\n" +
            "             186.37,180.86 177.23,184.58 172.00,187.58\n" +
            "             172.00,187.58 139.00,206.30 139.00,206.30\n" +
            "             135.38,208.30 126.57,213.91 123.00,214.36\n" +
            "             115.49,215.32 115.01,208.36 115.00,203.00\n" +
            "             115.00,203.00 115.00,154.00 115.00,154.00\n" +
            "             115.00,147.86 113.25,140.16 119.06,136.55 Z"

}