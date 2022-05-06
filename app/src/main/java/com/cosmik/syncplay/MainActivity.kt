package com.cosmik.syncplay

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cosmik.syncplay.main.MainFragment
import com.cosmik.syncplay.room.UserInteractionDelegate.UserInteractionListener

class MainActivity : AppCompatActivity() {


    private var userInteractionListener: UserInteractionListener? = null

    fun setUserInteractionListener(userInteractionListener: UserInteractionListener?) {
        this.userInteractionListener = userInteractionListener
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        userInteractionListener?.onUserInteraction()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                    .replace(R.id.mainactivity, MainFragment.newInstance())
                    .commitNow()
        }

        //This disables crashing.
        Thread.setDefaultUncaughtExceptionHandler { paramThread, paramThrowable ->
            Log.e(
                "Error ${Thread.currentThread().stackTrace[2]}",
                paramThrowable.localizedMessage!!
            )
            paramThrowable.printStackTrace()
        }


    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        for (fragment in supportFragmentManager.fragments) {
            fragment.onActivityResult(requestCode, resultCode, data)
        }
    }

}