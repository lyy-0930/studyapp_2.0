package com.studyapp

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL

class NetworkTestActivity : AppCompatActivity() {

    private lateinit var networkStatusText: TextView
    private lateinit var dnsStatusText: TextView
    private lateinit var ossDomainText: TextView
    private lateinit var logTextView: TextView
    private lateinit var testButton: Button
    private lateinit var backButton: Button

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    companion object {
        private const val TAG = "NetworkTestActivity"

        // 测试域名
        private const val TEST_DOMAIN_1 = "www.baidu.com"
        private const val TEST_DOMAIN_2 = "www.aliyun.com"
        private const val OSS_DOMAIN = "study-app-android-2026.oss-cn-hangzhou.aliyuncs.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_network_test)

        initViews()
        setupEventListeners()
    }

    private fun initViews() {
        networkStatusText = findViewById(R.id.networkStatusText)
        dnsStatusText = findViewById(R.id.dnsStatusText)
        ossDomainText = findViewById(R.id.ossDomainText)
        logTextView = findViewById(R.id.logTextView)
        testButton = findViewById(R.id.testButton)
        backButton = findViewById(R.id.backButton)
    }

    private fun setupEventListeners() {
        testButton.setOnClickListener {
            runNetworkTests()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun runNetworkTests() {
        testButton.isEnabled = false
        clearLog()

        coroutineScope.launch {
            addLog("开始网络诊断...")

            // 1. 检查网络连接状态
            val networkStatus = checkNetworkConnectivity()
            updateNetworkStatus(networkStatus)

            // 2. 测试DNS解析
            val dnsStatus = testDNSResolution()
            updateDNSStatus(dnsStatus)

            // 3. 测试OSS域名
            val ossStatus = testOSSDomain()
            updateOSSStatus(ossStatus)

            addLog("网络诊断完成")
            testButton.isEnabled = true
        }
    }

    private suspend fun checkNetworkConnectivity(): String {
        return withContext(Dispatchers.IO) {
            try {
                val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)

                    if (capabilities != null) {
                        when {
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
                                addLog("网络类型：WiFi")
                                "已连接 (WiFi)"
                            }
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                                addLog("网络类型：移动数据")
                                "已连接 (移动数据)"
                            }
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> {
                                addLog("网络类型：有线网络")
                                "已连接 (有线)"
                            }
                            else -> {
                                addLog("网络类型：未知")
                                "已连接 (未知类型)"
                            }
                        }
                    } else {
                        addLog("警告：网络能力为空")
                        "未连接"
                    }
                } else {
                    // 兼容旧版本
                    @Suppress("DEPRECATION")
                    val networkInfo = connectivityManager.activeNetworkInfo
                    if (networkInfo != null && networkInfo.isConnected) {
                        addLog("网络类型：${networkInfo.typeName}")
                        "已连接 (${networkInfo.typeName})"
                    } else {
                        addLog("警告：网络信息为空或未连接")
                        "未连接"
                    }
                }
            } catch (e: Exception) {
                addLog("检查网络连接时出错: ${e.message}")
                "检查失败: ${e.message}"
            }
        }
    }

    private suspend fun testDNSResolution(): String {
        return withContext(Dispatchers.IO) {
            val results = mutableListOf<String>()

            // 测试常见域名
            val testDomains = listOf(
                TEST_DOMAIN_1 to "百度",
                TEST_DOMAIN_2 to "阿里云"
            )

            for ((domain, name) in testDomains) {
                try {
                    addLog("解析域名: $domain ($name)...")
                    val addresses = InetAddress.getAllByName(domain)
                    val ipList = addresses.joinToString(", ") { it.hostAddress }
                    results.add("✓ $name ($domain): $ipList")
                    addLog("成功: $domain -> $ipList")
                } catch (e: Exception) {
                    results.add("✗ $name ($domain): 解析失败 - ${e.message}")
                    addLog("失败: $domain - ${e.message}")
                }
            }

            results.joinToString("\n")
        }
    }

    private suspend fun testOSSDomain(): String {
        return withContext(Dispatchers.IO) {
            try {
                addLog("测试OSS域名: $OSS_DOMAIN")

                // 1. 尝试DNS解析
                addLog("1. 尝试DNS解析...")
                try {
                    val addresses = InetAddress.getAllByName(OSS_DOMAIN)
                    val ipList = addresses.joinToString(", ") { it.hostAddress }
                    addLog("DNS解析成功: $OSS_DOMAIN -> $ipList")
                } catch (e: Exception) {
                    addLog("DNS解析失败: ${e.message}")
                    return@withContext "DNS解析失败: ${e.message}"
                }

                // 2. 尝试HTTP连接（注意：OSS可能需要HTTPS，这里只测试连接）
                addLog("2. 尝试TCP连接...")
                try {
                    val url = URL("https://$OSS_DOMAIN")
                    val connection = url.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000

                    // 尝试连接
                    connection.connect()

                    // 获取响应码（可能因为认证失败而返回403，但连接是成功的）
                    val responseCode = (connection as? java.net.HttpURLConnection)?.responseCode
                    addLog("连接成功，响应码: $responseCode")

                    if (responseCode == 403) {
                        "连接成功，但访问被拒绝（需要认证）"
                    } else if (responseCode in 200..299) {
                        "连接成功，响应码: $responseCode"
                    } else {
                        "连接成功，但响应异常: $responseCode"
                    }
                } catch (e: java.net.UnknownHostException) {
                    addLog("未知主机错误: ${e.message}")
                    "未知主机: ${e.message}"
                } catch (e: java.net.SocketTimeoutException) {
                    addLog("连接超时: ${e.message}")
                    "连接超时: ${e.message}"
                } catch (e: java.io.IOException) {
                    addLog("IO错误: ${e.message}")
                    "连接错误: ${e.message}"
                } catch (e: Exception) {
                    addLog("其他错误: ${e.message}")
                    "测试失败: ${e.message}"
                }
            } catch (e: Exception) {
                addLog("测试OSS域名时发生异常: ${e.message}")
                "测试异常: ${e.message}"
            }
        }
    }

    private fun updateNetworkStatus(status: String) {
        runOnUiThread {
            networkStatusText.text = "网络状态：$status"
        }
    }

    private fun updateDNSStatus(status: String) {
        runOnUiThread {
            dnsStatusText.text = "DNS解析：\n$status"
        }
    }

    private fun updateOSSStatus(status: String) {
        runOnUiThread {
            ossDomainText.text = "OSS域名：$status"
        }
    }

    private fun addLog(message: String) {
        runOnUiThread {
            val timestamp = System.currentTimeMillis() % 100000
            val logLine = "[$timestamp] $message"
            logTextView.append("$logLine\n")

            // 滚动到底部
            val scrollAmount = logTextView.layout.getLineTop(logTextView.lineCount) - logTextView.height
            if (scrollAmount > 0) {
                logTextView.scrollTo(0, scrollAmount)
            } else {
                logTextView.scrollTo(0, 0)
            }

            // 同时在Logcat中输出
            Log.d(TAG, message)
        }
    }

    private fun clearLog() {
        runOnUiThread {
            logTextView.text = "日志输出：\n"
        }
    }
}