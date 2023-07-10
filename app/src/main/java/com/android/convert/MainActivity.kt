package com.android.convert

import android.Manifest
import android.os.Bundle
import android.os.Message
import android.text.TextUtils
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.android.apphelper2.utils.*
import com.android.apphelper2.utils.permission.PermissionMultipleCallBackListener
import com.android.apphelper2.utils.permission.PermissionUtil
import com.android.apphelper2.utils.zmq.ZmqUtil2
import com.android.convert.databinding.ActivityMainBinding
import com.android.keeplife.account.LifecycleManager
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
    private val mNetWorkUtil: NetworkUtil by lazy {
        return@lazy NetworkUtil.instance.register()
    }
    private val permissionUtil = PermissionUtil.PermissionActivity(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mBinding = ActivityMainBinding.inflate(layoutInflater, null, false)

        setContentView(mBinding.root)

        ZmqUtil2.initLog(this)

        initData()
    }

    private fun initData() {
        initKeepLife()

        // handler 数据的展示
        mHandler.setHandlerCallBackListener(object : HandlerUtil.HandlerMessageListener {
            override fun handleMessage(msg: Message) {
                if (msg.what == 100) {
                    val obj = msg.obj as String
                    val split = obj.split("|")
                    mBinding.tvSend.text = split[0]
                    mBinding.tvResult.text = split[1]
                } else if (msg.what == 200) {
                    val obj = msg.obj as String
                    mBinding.tvZmqResult.text = obj
                    mBinding.tvZmqSend.text = "数据接收中..."
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

            // 接收zmq 数据
            lifecycleScope.launch(Dispatchers.IO) {
                ZmqUtil2.setCallBackListener {
                    val message = mHandler.getMessage()
                    message.what = 200
                    message.obj = it
                    mHandler.send(message)

                    // 发送到 socket
                    mSocketUtil.sendServerData(it)
                }
            }
        }

        // zmq 数据断联的监听
        lifecycleScope.launch {
            ZmqUtil2.setConnectionLostListener {
                mBinding.tvZmqSend.post {
                    mBinding.tvZmqSend.text = "ZMQ发送端数据异常，请尽快检查！"
                }
            }
        }

        // zmq 操作的监听
        lifecycleScope.launch(Dispatchers.IO) {
            ZmqUtil2.setTraceListener(object : ZmqUtil2.TraceListener {
                override fun trace(content: String) {
                    mBinding.tvZmqSend.post {
                        mBinding.tvZmqSend.text = content
                    }
                }
            })
        }

        mSocketUtil.initSocketService()
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

        // 检查ip
        mBinding.btnCheckIp.setOnClickListener {
            lifecycleScope.launch {
                mNetWorkUtil.getIPAddress {
                    mBinding.tvIp.text = "当前的ip:$it"
                    mBinding.etIp.setText(it)
                }
            }
        }

        // 初始化zmq
        mBinding.btnReceiver.setOnClickListener {
            lifecycleScope.launch {
                val ip = mBinding.etIp.text.toString()
                if (TextUtils.isEmpty(ip)) {
                    ToastUtil.show("Ip is empty !")
                    return@launch
                }
                ZmqUtil2.IP_ADDRESS = ip
                ZmqUtil2.start()
            }
        }
    }

    private fun initKeepLife() {
        permissionUtil.requestArray(
            arrayOf(Manifest.permission.GET_ACCOUNTS, Manifest.permission.WRITE_SYNC_SETTINGS, Manifest.permission.FOREGROUND_SERVICE),
            object : PermissionMultipleCallBackListener {
                override fun onCallBack(allGranted: Boolean, map: MutableMap<String, Boolean>) {
                    LifecycleManager.instance.paddingActivity(this@MainActivity.packageName,
                        FileUtil.instance.getCanonicalNamePath(this@MainActivity::class.java))
                    LifecycleManager.instance.startLifecycle(this@MainActivity)
                    ToastUtil.show("开启保活服务")
                }
            })
    }

    override fun onDestroy() {
        super.onDestroy()
        mSocketUtil.stop()
    }
}