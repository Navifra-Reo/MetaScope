package com.zlegamer.metascope

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.zlegamer.metascope.databinding.ActivityAuthBinding
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL

class AuthActivity : AppCompatActivity() {
    lateinit var viewBinding : ActivityAuthBinding
    lateinit var iv_image : ImageView
    private lateinit var mGoogleSignInClient : GoogleSignInClient
    private val RC_SIGN_IN = 9001

    override fun onCreate(savedInstanceState : Bundle?) {

        super.onCreate(savedInstanceState)
        viewBinding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)


        val gso : GoogleSignInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
        val account = GoogleSignIn.getLastSignedInAccount(this)
        viewBinding.signInButton.setOnClickListener {
            signIn()
        }
    }

    private fun signIn() {
        var signInIntent: Intent = mGoogleSignInClient.getSignInIntent()
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        Log.d("account", "로그인 페이지")
        try {
            val account = completedTask.getResult(ApiException::class.java)

            val displayName = account?.displayName.toString()
            val profileImg = account?.photoUrl.toString()
            Log.d("account", profileImg)

            val intent = Intent(this, LobbyActivity::class.java)
            intent.putExtra("image",profileImg);
            intent.putExtra("Name",displayName);
            startActivity(intent)
            finish()
        }
        catch (e: ApiException) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w("failed", "signInResult:failed code=" + e.statusCode)
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        Log.d("account", "로그인 시도")
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            Log.d("account", "로그인 태스크 생성")
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            val task: Task<GoogleSignInAccount> = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
        }
    }
}