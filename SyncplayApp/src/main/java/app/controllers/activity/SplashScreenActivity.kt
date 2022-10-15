package app.controllers.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import app.utils.MiscUtils

@SuppressLint("CustomSplashScreen") /* Don't trust Google's SplashScreen API, we make our own */
class SplashScreenActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* Enable IMMERSIVE MODE right off the bat */
        MiscUtils.hideSystemUI(this, false)

        /* Delaying transition */
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this@SplashScreenActivity, MainActivity::class.java))
            finish()
        }, 500)


    }

}