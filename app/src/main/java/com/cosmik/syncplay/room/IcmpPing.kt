package com.cosmik.syncplay.room

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class IcmpPing {
    private var result: Double = 0.13
    fun pingIcmp(host: String, packet: Int): Double {
        try {
                val pingprocess: Process? = Runtime.getRuntime().exec("/system/bin/ping -c 1 -w 1 -s $packet $host")
                //Reading the Output with BufferedReader
                val bufferedReader = BufferedReader(InputStreamReader(pingprocess?.inputStream))
                //Parsing the result in a string variable.
                val logger: StringBuilder = StringBuilder()
                var line: String? = ""
                while (line != null) {
                    line = bufferedReader.readLine()
                    logger.append(line + "\n")
                }
                val pingoutput = logger.toString()

                //Now reading what we have in pingResult and storing it as an Int value.
                result = when {
                    pingoutput.contains("100% packet loss") -> {
                        0.2
                    }
                    else -> {
                        ((pingoutput.substringAfter("time=").substringBefore(" ms").trim()
                            .toDouble())/1000.0)
                    }
                }

        } catch (e: IOException) { }

        return result

    }
}