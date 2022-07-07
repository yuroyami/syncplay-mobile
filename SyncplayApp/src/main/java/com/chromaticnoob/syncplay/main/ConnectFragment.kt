package com.chromaticnoob.syncplay.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.chromaticnoob.syncplay.BuildConfig
import com.chromaticnoob.syncplay.R
import com.chromaticnoob.syncplay.databinding.FragmentConnectBinding
import com.chromaticnoob.syncplay.room.RoomActivity
import com.google.gson.GsonBuilder

@SuppressLint("SetTextI18n")
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

        binding.connectFootnoteB.text = "v" + BuildConfig.VERSION_NAME
        binding.connectJoinButton.setOnClickListener {
            val joiningInfo: MutableList<Any?> = mutableListOf()

            val customServerCheck = binding.connectCustomServerAddress.isVisible

            val serverAddress =
                if (customServerCheck) binding.connectCustomServerAddress.text.toString() else ""

            val serverPort =
                if (customServerCheck) binding.connectCustomServerPort.text.toString().toIntOrNull()
                else (binding.spMenuAutocomplete.text).toString().substringAfter("syncplay.pl:")
                    .toIntOrNull()

            if (serverPort == null) {
                binding.connectCustomServerPort.error = "Select port !"
                return@setOnClickListener
            }

            var username = binding.connectUsernameInputText.text.toString().replace("\\", "")
                .trim()

            username.also {
                if (it.length > 150) username = it.substring(0, 149)
                if (it.isEmpty()) {
                    username = it.plus("Anonymous${(1000..9999).random()}")
                }
            }

            var roomname = binding.connectRoomnameInputText.text.toString().replace("\\", "")
                .trim()

            roomname.also {
                if (it.length > 35) roomname = it.substring(0, 34)
                if (it.isEmpty()) {
                    binding.connectRoomnameInput.error =
                        getString(R.string.connect_roomname_empty_error)
                    return@setOnClickListener
                }
            }

            var password = ""
            joiningInfo.add(0, serverAddress)
            joiningInfo.add(1, serverPort)
            joiningInfo.add(2, username)
            joiningInfo.add(3, roomname)
            if (customServerCheck && binding.connectCustomServerPassword.text.isNotBlank()) {
                password = binding.connectCustomServerPassword.text.toString()
                joiningInfo.add(4, password)
            } else {
                joiningInfo.add(4, null)
            }

            val sharedPref = requireActivity().getPreferences(Context.MODE_PRIVATE)
            with(sharedPref.edit()) {
                putString("server", binding.spMenuAutocomplete.text.toString())
                putString("username", username)
                putString("roomname", roomname)
                if (customServerCheck) {
                    putString("customAddress", serverAddress)
                    putInt("customPort", serverPort)
                    putString("customPassord", password)
                }
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
        binding.spMenuAutocomplete.setAdapter(
            ArrayAdapter(
                requireContext(),
                R.layout.serverlist_textview,
                servers
            )
        )

        /** Listening to the event where users click 'Enter Custom Server' or other servers */
        binding.spMenuAutocomplete.setOnItemClickListener { _, _, i, _ ->
            if (i == 5) {
                toggleCustomServer(true)
            } else {
                toggleCustomServer(false)
            }
        }

        if (PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getBoolean("save_info", true)
        ) {
            val sp = requireActivity().getPreferences(Context.MODE_PRIVATE)
            val savedServer = sp.getString("server", "syncplay.pl:8997")
            binding.spMenuAutocomplete.setText(savedServer, false)
            if (savedServer == getString(R.string.connect_enter_custom_server)) {
                toggleCustomServer(true)
                binding.connectCustomServerAddress.setText(sp.getString("customAddress", ""))
                binding.connectCustomServerPort.setText(sp.getInt("customPort", 0).toString())
                binding.connectCustomServerPassword.setText(sp.getString("customPassword", ""))
            }
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
            binding.spMenuAutocomplete.setText("syncplay.pl:8997")
            binding.connectUsernameInputText.setText("user_" + (0..9999).random())
            binding.connectRoomnameInputText.setText("room_" + (0..9999).random())
        }
    }

    private fun toggleCustomServer(show: Boolean) {
        when (show) {
            true -> {
                binding.connectCustomServerAddress.visibility = View.VISIBLE
                binding.connectCustomServerPort.visibility = View.VISIBLE
                binding.connectCustomServerPassword.visibility = View.VISIBLE
                binding.pageConnect.smoothScrollBy(0, 10000, 100)
            }
            false -> {
                binding.connectCustomServerAddress.visibility = View.GONE
                binding.connectCustomServerPort.visibility = View.GONE
                binding.connectCustomServerPassword.visibility = View.GONE
            }
        }
    }
}