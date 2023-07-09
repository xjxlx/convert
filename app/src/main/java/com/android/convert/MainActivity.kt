package com.android.convert

import android.os.Bundle
import android.os.Message
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.apphelper2.utils.HandlerUtil
import com.android.apphelper2.utils.SocketUtil
import com.android.convert.databinding.ActivityMainBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var mBinding: ActivityMainBinding
    private val mSocketUtil: SocketUtil.SocketService by lazy {
        return@lazy SocketUtil.SocketService()
    }
    private val mHandler: HandlerUtil by lazy {
        return@lazy HandlerUtil()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater, null, false)

        setContentView(mBinding.root)

        initData()
    }

    private fun initData() {
        mHandler.setHandlerCallBackListener(object : HandlerUtil.HandlerMessageListener {
            override fun handleMessage(msg: Message) {
                if (msg.what == 100) {
                    val obj = msg.obj as String
                    val split = obj.split("|")
                    mBinding.tvSend.text = split[0]
                    mBinding.tvResult.text = split[1]
                }
            }
        })

        lifecycleScope.launch {
            mSocketUtil.setServiceCallBackListener(object : SocketUtil.SocketService.ServerCallBackListener {
                override fun callBack(send: String, result: String) {
                    val message = mHandler.getMessage()
                    message.what = 100
                    message.obj = "$send|$result"
                    mHandler.send(message)
                }
            })
        }

        mBinding.btnInitSocket.setOnClickListener {
            mSocketUtil.initSocketService()
        }

        mBinding.btnSend.setOnClickListener {
            lifecycleScope.launch {
                repeat(Int.MAX_VALUE) {
                    mSocketUtil.sendServerData("服务端-->发送-->$it")
                    delay(200)
                }
            }
        }
    }
}