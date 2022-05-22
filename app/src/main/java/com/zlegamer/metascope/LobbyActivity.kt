package com.zlegamer.metascope

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.bumptech.glide.Glide
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zlegamer.metascope.databinding.ActivityLobbyBinding
import io.socket.client.IO
import io.socket.client.Socket
import io.socket.emitter.Emitter
import org.json.JSONObject
import java.net.URISyntaxException


class LobbyActivity : AppCompatActivity() {
    lateinit var name : String
    lateinit var profileImg : String
    lateinit var roomName : String

    var roomSocket: Socket? = null
    var gson: Gson = Gson()

    var readyCheck : Boolean = false;

    private lateinit var viewBinding: ActivityLobbyBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lobby)

        viewBinding = ActivityLobbyBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        val intent : Intent = getIntent();
        name = intent.getStringExtra("Name").toString()
        profileImg = intent.getStringExtra("image").toString()

        Log.d("프로필 이미지 주소",profileImg)
        Glide.with(this).load(profileImg).override(200,200).into(viewBinding.imageView)

        viewBinding.username.text = name

        //방 정보 업데이트
        if(roomSocket != null) roomSocket!!.disconnect()
        try {
            roomSocket = IO.socket("http://192.168.1.52:3000")
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        roomSocket!!.connect()
        viewBinding.btnEnter.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                roomName = viewBinding.roomNameEnter.getText().toString().trim()
                val jsonObject = JsonObject();
                jsonObject.addProperty("username", name);
                jsonObject.addProperty("roomname", roomName);

                roomSocket!!.emit(
                    "connect",
                    gson.toJson(jsonObject)
                )
                viewBinding.enter.setVisibility(View.INVISIBLE)
                viewBinding.ready.setVisibility(View.INVISIBLE)
                viewBinding.search.setVisibility(View.VISIBLE)

                readyCheck=true;

                roomSocket!!.on("ready",whenroomready);
                roomSocket!!.on("enter",whenuserenter);
            }
        })
    }

    //방에 누군가 입장했을때
    var whenuserenter = Emitter.Listener { args ->
        runOnUiThread {
            val data: JSONObject = args[0] as JSONObject
            val msg = data[name].toString()+"님이 입장하셨습니다   "+data["count"]+"/3"
            Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show()
        }
    }
    var whenroomready = Emitter.Listener { args ->
        runOnUiThread {
            //Toast.makeText(MainActivity.this, pnames[0]+" "+pnames[1] ,Toast.LENGTH_LONG).show();
            viewBinding.enter.setVisibility(View.INVISIBLE)
            viewBinding.ready.setVisibility(View.VISIBLE)
            viewBinding.search.setVisibility(View.INVISIBLE)

            //animation play
            val anim = findViewById<View>(R.id.img_ready) as LottieAnimationView
            anim.playAnimation()

            //when ready button pressed
            viewBinding.btnReady.setOnClickListener(View.OnClickListener {

                if(readyCheck){
                    readyCheck=false;
                }
                val jsonObject = JsonObject();
                jsonObject.addProperty("username", name);
                jsonObject.addProperty("roomname", roomName);

                roomSocket!!.emit(
                    "ready",
                    gson.toJson({jsonObject})
                )
                roomSocket!!.on("accept", accpet)
            })
        }
    }
    //게임 시작
    var accpet = Emitter.Listener {
        runOnUiThread(Runnable {
            val intent = Intent(this@LobbyActivity, MainActivity::class.java)
            intent.putExtra("Name",name)
            intent.putExtra("Room",roomName)
            startActivity(intent)
        })
    }
}