package com.cosmik.syncplay.main

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
import com.cosmik.syncplay.R
import com.cosmik.syncplay.databinding.FragmentConnectBinding
import com.cosmik.syncplay.room.RoomActivity
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

        val servers = listOf(
            "syncplay.pl:8995",
            "syncplay.pl:8996",
            "syncplay.pl:8997",
            "syncplay.pl:8998",
            "syncplay.pl:8999"
        )

        val adapter = ArrayAdapter(requireContext(), R.layout.serverlist_textview, servers)
        binding.spMenuAutocomplete.setAdapter(adapter)

        binding.connectJoinButton.setOnClickListener {
            val joiningInfo: MutableList<Any> = mutableListOf()

            val serverPort =
                (binding.spMenuAutocomplete.text).toString().substringAfter("syncplay.pl:").toInt()

            val username = binding.connectUsernameInputText.text.toString().replace("\\", "")
                .trim().also {
                    if (it.length > 150) it.substring(0, 149)
                    if (it.isEmpty()) {
                        binding.connectUsernameInput.error = "Username shouldn't be empty"
                        return@setOnClickListener
                    }
                }

            val roomname = binding.connectRoomnameInputText.text.toString().replace("\\", "")
                .trim().also {
                    if (it.length > 35) it.substring(0, 34)
                    if (it.isEmpty()) {
                        binding.connectRoomnameInput.error = "Room name shouldn't be empty"
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
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onResume() {
        super.onResume()

        if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("save_info", true)
        ) {
            val sp = requireActivity().getPreferences(Context.MODE_PRIVATE)
            binding.spMenuAutocomplete.setText(sp.getString("server", "syncplay.pl:8999"), false)
            binding.connectUsernameInputText.setText(
                sp.getString(
                    "username",
                    "user_" + (0..9999).random()
                )
            )
            binding.connectRoomnameInputText.setText(
                sp.getString(
                    "roomname",
                    "room_" + (0..9999).random()
                )
            )
        } else {
            binding.spMenuAutocomplete.setText("syncplay.pl:8999")
            binding.connectUsernameInputText.setText(
                "user_" + (0..9999).random()

            )
            binding.connectRoomnameInputText.setText(
                "room_" + (0..9999).random()

            )
        }
    }


}