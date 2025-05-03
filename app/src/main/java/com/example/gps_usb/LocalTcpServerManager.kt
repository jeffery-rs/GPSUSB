package com.example.gps_usb

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * 管理本地TCP服务器，专门绑定到127.0.0.1
 * 处理客户端连接并发送GPS数据
 * 
 * @param port 服务器监听的端口号
 * @param scope 用于启动协程的CoroutineScope
 * @param onClientCountChanged 客户端连接数变化时的回调
 */
class LocalTcpServerManager(
    private val port: Int,
    private val scope: CoroutineScope,
    private val onClientCountChanged: (Int) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val clientMap = ConcurrentHashMap<Socket, OutputStream>()
    private val clientCount = AtomicInteger(0)
    
    /**
     * 启动TCP服务器，监听指定端口的连接请求
     * 
     * @return 是否成功启动服务器
     */
    fun startServer(): Boolean {
        return try {
            // 严格绑定到127.0.0.1（localhost）
            val loopbackAddress = InetAddress.getByName("127.0.0.1")
            serverSocket = ServerSocket(port, 1, loopbackAddress)
            
            serverJob = scope.launch(Dispatchers.IO) {
                try {
                    while (true) {
                        val clientSocket = serverSocket?.accept() ?: break
                        handleClientConnection(clientSocket)
                    }
                } catch (e: IOException) {
                    // 服务器关闭或出现IO错误时退出循环
                }
            }
            true
        } catch (e: IOException) {
            false
        }
    }
    
    /**
     * 处理新的客户端连接
     */
    private fun handleClientConnection(clientSocket: Socket) {
        try {
            val outputStream = clientSocket.getOutputStream()
            clientMap[clientSocket] = outputStream
            
            val newCount = clientCount.incrementAndGet()
            onClientCountChanged(newCount)
            
        } catch (e: IOException) {
            clientSocket.close()
        }
    }
    
    /**
     * 向所有连接的客户端发送数据
     * 
     * @param jsonDataWithNewline 包含换行符的JSON数据字符串
     */
    suspend fun sendDataToClients(jsonDataWithNewline: String) = withContext(Dispatchers.IO) {
        val dataBytes = jsonDataWithNewline.toByteArray(Charsets.UTF_8)
        val socketsToRemove = mutableListOf<Socket>()
        
        clientMap.forEach { (socket, outputStream) ->
            try {
                outputStream.write(dataBytes)
                outputStream.flush()
            } catch (e: IOException) {
                // 客户端可能已断开连接
                socketsToRemove.add(socket)
            }
        }
        
        // 移除断开连接的客户端
        socketsToRemove.forEach { socket ->
            removeClient(socket)
        }
    }
    
    /**
     * 移除并关闭一个客户端连接
     */
    private fun removeClient(socket: Socket) {
        clientMap.remove(socket)
        try {
            socket.close()
        } catch (e: IOException) {
            // 忽略关闭时的错误
        }
        
        val newCount = clientCount.decrementAndGet()
        onClientCountChanged(newCount)
    }
    
    /**
     * 停止服务器并清理所有资源
     */
    fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        
        // 关闭所有客户端连接
        clientMap.keys.forEach { socket ->
            try {
                socket.close()
            } catch (e: IOException) {
                // 忽略关闭时的错误
            }
        }
        clientMap.clear()
        clientCount.set(0)
        onClientCountChanged(0)
        
        // 关闭服务器套接字
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: IOException) {
            // 忽略关闭时的错误
        }
    }
    
    /**
     * 获取当前连接的客户端数量
     */
    fun getClientCount(): Int = clientCount.get()
} 