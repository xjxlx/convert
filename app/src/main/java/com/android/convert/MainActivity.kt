package com.android.convert

import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.apphelper2.utils.HandlerUtil
import com.android.apphelper2.utils.SocketUtil
import com.android.apphelper2.utils.ToastUtil
import com.android.apphelper2.utils.zmq.ZmqUtil6
import com.android.convert.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
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
    private val mZmqUtil: ZmqUtil6.Result by lazy {
        return@lazy ZmqUtil6.Result()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

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
                } else if (msg.what == 200) {
                    val obj = msg.obj as String
                    val split = obj.split("|")
                    mBinding.tvZmqSend.text = split[0]
                    mBinding.tvZmqResult.text = split[1]
                }
            }
        })

        lifecycleScope.launch {
            // 接收sock数据
            mSocketUtil.setServiceCallBackListener(object : SocketUtil.SocketService.ServerCallBackListener {
                override fun callBack(send: String, result: String) {
                    val message = mHandler.getMessage()
                    message.what = 100
                    message.obj = "$send|$result"
                    mHandler.send(message)
                }
            })

            // 接收zmq数据
            mZmqUtil.setResultCallBackListener(object : ZmqUtil6.Result.ResultCallBackListener {
                override fun onCall(send: String, result: String) {
                    val message = mHandler.getMessage()
                    message.what = 200
                    message.obj = "$send|$result"
                    mHandler.send(message)
                }
            })
        }

        // 初始化socket
        mBinding.btnInitSocket.setOnClickListener {
            mSocketUtil.initSocketService()
        }

        // 停止socket
        mBinding.btnClose.setOnClickListener {
            mSocketUtil.stop()
        }

        // 发送 socket 数据
        mBinding.btnSend.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                repeat(Int.MAX_VALUE) {
                    val sendServerData = mSocketUtil.sendServerData("服务端-->发送-->$it")
                    if (!sendServerData) {
                        cancel()
                    }
                    delay(200)
                }
            }
        }

        // 初始化zmq
        mBinding.btnReceiver.setOnClickListener {
            val ip = mBinding.tvIp.text.toString()
            if (TextUtils.isEmpty(ip)) {
                ToastUtil.show("Ip is empty !")
                return@setOnClickListener
            }
            val tcp = "tcp://" + ip + ":" + ZmqUtil6.port
            mZmqUtil.initResultZmq(tcp)
        }
    }
}