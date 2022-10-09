package app.controllers.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.R
import app.databinding.PopupInroomSettingsHosterBinding

class RoomSettingsHosterFragment : Fragment() {

    private lateinit var binding: PopupInroomSettingsHosterBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = PopupInroomSettingsHosterBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.uisettings_container, RoomSettingsFragment())
            .commit()
    }
}