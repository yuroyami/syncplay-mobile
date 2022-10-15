package app.pseudopopups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.R
import app.databinding.FragmentInroomSettingsHosterBinding

class RoomSettingsHosterFragment : Fragment() {

    private lateinit var binding: FragmentInroomSettingsHosterBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = FragmentInroomSettingsHosterBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.uisettings_container, RoomSettingsFragment())
            .commit()
    }
}