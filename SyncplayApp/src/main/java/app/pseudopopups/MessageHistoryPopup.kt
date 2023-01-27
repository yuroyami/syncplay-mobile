package app.pseudopopups

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import app.databinding.FragmentMessageHistoryBinding
import app.ui.activities.RoomActivity

class MessageHistoryPopup : Fragment() {

    private lateinit var binding: FragmentMessageHistoryBinding

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, sis: Bundle?): View {
        binding = FragmentMessageHistoryBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val a = (requireActivity() as RoomActivity)
        //binding.syncplayMESSAGEHISTORYRecyc.adapter = HistoryRecycAdapter(a.p.session.messageSequence, requireContext())
    }
}