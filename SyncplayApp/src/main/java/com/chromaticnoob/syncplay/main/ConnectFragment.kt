package com.chromaticnoob.syncplay.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.chromaticnoob.syncplay.R
import com.chromaticnoob.syncplay.databinding.FragmentConnectBinding
import com.chromaticnoob.syncplay.room.RoomActivity
import com.google.gson.GsonBuilder


class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = FragmentConnectBinding.bind(view)

        binding.connectJoinButton.setOnClickListener {
            val joiningInfo: MutableList<Any> = mutableListOf()

            val serverPort =
                (binding.spMenuAutocomplete.text).toString().substringAfter("syncplay.pl:").toInt()

            val username = binding.connectUsernameInputText.text.toString().replace("\\", "")
                .trim().also {
                    if (it.length > 150) it.substring(0, 149)
                    if (it.isEmpty()) {
                        binding.connectUsernameInput.error =
                            getString(R.string.connect_username_empty_error)
                        return@setOnClickListener
                    }
                }

            val roomname = binding.connectRoomnameInputText.text.toString().replace("\\", "")
                .trim().also {
                    if (it.length > 35) it.substring(0, 34)
                    if (it.isEmpty()) {
                        binding.connectRoomnameInput.error =
                            getString(R.string.connect_roomname_empty_error)
                        return@setOnClickListener
                    }
                }

            joiningInfo.add(0, serverPort)
            joiningInfo.add(1, username)
            joiningInfo.add(2, roomname)

            val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("server", binding.spMenuAutocomplete.text.toString())
                putString("username", username)
                putString("roomname", roomname)
                apply()
            }

            val json = GsonBuilder().create().toJson(joiningInfo)
            val intent = Intent(requireContext(), RoomActivity::class.java).apply {
                putExtra("json", json)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)


        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        /** Deploying our list of available servers */
        val servers = listOf(
            "syncplay.pl:8995",
            "syncplay.pl:8996",
            "syncplay.pl:8997",
            "syncplay.pl:8998",
            "syncplay.pl:8999",
            getString(R.string.connect_enter_custom_server)
        )

        /** Instantiating the adapter we need for the Material EditText */
        val adapter = ArrayAdapter(requireContext(), R.layout.serverlist_textview, servers)
        binding.spMenuAutocomplete.setAdapter(adapter)

        /** Listening to the event where users click 'Enter Custom Server' or other servers */
        binding.spMenuAutocomplete.setOnItemClickListener { _, _, i, _ ->
            if (i == 5) {
                binding.connectCustomServerAddress.visibility = View.VISIBLE
                binding.connectCustomServerPort.visibility = View.VISIBLE
            } else {
                binding.connectCustomServerAddress.visibility = View.GONE
                binding.connectCustomServerPort.visibility = View.GONE
            }
        }

        if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("save_info", true)
        ) {
            val sp = requireActivity().getPreferences(Context.MODE_PRIVATE)
            binding.spMenuAutocomplete.setText(sp.getString("server", "syncplay.pl:8997"), false)
            binding.connectUsernameInputText
                .setText(sp.getString("username", "user_" + (0..9999).random()))
            binding.connectRoomnameInputText
                .setText(sp.getString("roomname", "room_" + (0..9999).random()))
        } else {
            binding.spMenuAutocomplete.setText("syncplay.pl:8997")
            binding.connectUsernameInputText.setText("user_" + (0..9999).random())
            binding.connectRoomnameInputText.setText("room_" + (0..9999).random())
        }
    }


}