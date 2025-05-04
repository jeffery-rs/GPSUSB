#include <iostream>
#include <string>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <windows.h>
#include <cstdio>

#pragma comment(lib, "ws2_32.lib") // 链接到Winsock库

/**
 * 设置ADB端口转发，将手机端口12345映射到电脑端口54321
 * @return 设置成功返回true，失败返回false
 */
bool setup_adb_forward() {
    std::cout << "设置ADB端口转发" << std::endl;

    // 创建命令执行进程
    SECURITY_ATTRIBUTES saAttr;
    saAttr.nLength = sizeof(SECURITY_ATTRIBUTES);
    saAttr.bInheritHandle = TRUE;
    saAttr.lpSecurityDescriptor = NULL;

    HANDLE hChildStdoutRd, hChildStdoutWr;

    // 创建管道
    if (!CreatePipe(&hChildStdoutRd, &hChildStdoutWr, &saAttr, 0)) {
        std::cerr << "创建管道失败" << std::endl;
        return false;
    }

    // 确保读句柄不被子进程继承
    if (!SetHandleInformation(hChildStdoutRd, HANDLE_FLAG_INHERIT, 0)) {
        std::cerr << "设置句柄信息失败" << std::endl;
        CloseHandle(hChildStdoutRd);
        CloseHandle(hChildStdoutWr);
        return false;
    }

    // 设置进程启动信息
    STARTUPINFOA startupInfo;
    ZeroMemory(&startupInfo, sizeof(startupInfo));
    startupInfo.cb = sizeof(startupInfo);
    startupInfo.hStdError = hChildStdoutWr;
    startupInfo.hStdOutput = hChildStdoutWr;
    startupInfo.dwFlags |= STARTF_USESTDHANDLES;

    PROCESS_INFORMATION processInfo;
    ZeroMemory(&processInfo, sizeof(processInfo));

    // 启动ADB进程
    char cmdLine[] = "adb forward tcp:54321 tcp:12345";
    if (!CreateProcessA(
        NULL,           // 应用程序名称
        cmdLine,        // 命令行
        NULL,           // 进程安全属性
        NULL,           // 线程安全属性
        TRUE,           // 句柄继承
        0,              // 创建标志
        NULL,           // 环境块
        NULL,           // 当前目录
        &startupInfo,   // 启动信息
        &processInfo    // 进程信息
    )) {
        std::cerr << "创建进程失败，错误码: " << GetLastError() << std::endl;
        CloseHandle(hChildStdoutRd);
        CloseHandle(hChildStdoutWr);
        return false;
    }

    // 关闭写入端，我们不需要它
    CloseHandle(hChildStdoutWr);

    // 等待进程完成
    WaitForSingleObject(processInfo.hProcess, INFINITE);

    // 检查进程退出代码
    DWORD exitCode;
    GetExitCodeProcess(processInfo.hProcess, &exitCode);

    // 读取输出
    char buffer[4096];
    DWORD bytesRead;
    std::string result;

    while (ReadFile(hChildStdoutRd, buffer, sizeof(buffer) - 1, &bytesRead, NULL) && bytesRead > 0) {
        buffer[bytesRead] = '\0';
        result += buffer;
    }

    // 关闭句柄
    CloseHandle(processInfo.hProcess);
    CloseHandle(processInfo.hThread);
    CloseHandle(hChildStdoutRd);

    if (exitCode == 0) {
        std::cout << "成功设置ADB端口转发" << std::endl;
        return true;
    }
    else {
        std::cerr << "ADB端口转发设置失败，返回码: " << exitCode << std::endl;
        if (!result.empty()) {
            std::cerr << "错误输出: " << result << std::endl;
        }
        return false;
    }
}

/**
 * 连接到GPS服务并接收数据
 */
void connect_to_gps() {
    SOCKET sock = INVALID_SOCKET;
    struct sockaddr_in serv_addr;

    // 初始化Winsock
    WSADATA wsaData;
    int result = WSAStartup(MAKEWORD(2, 2), &wsaData);
    if (result != 0) {
        std::cerr << "WSAStartup失败: " << result << std::endl;
        return;
    }

    // 创建套接字
    sock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
    if (sock == INVALID_SOCKET) {
        std::cerr << "套接字创建失败: " << WSAGetLastError() << std::endl;
        WSACleanup();
        return;
    }

    // 设置服务器地址和端口
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(54321);
    inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr);

    // 连接到服务器
    result = connect(sock, (struct sockaddr*)&serv_addr, sizeof(serv_addr));
    if (result == SOCKET_ERROR) {
        std::cerr << "连接失败: " << WSAGetLastError() << std::endl;
        closesocket(sock);
        WSACleanup();
        return;
    }

    std::cout << "已连接到GPS服务" << std::endl;

    // 持续接收数据
    char buffer[1025];
    int bytesReceived;
    while (true) {
        memset(buffer, 0, sizeof(buffer));
        bytesReceived = recv(sock, buffer, 1024, 0);

        if (bytesReceived <= 0) {
            break;
        }

        std::cout << "收到GPS数据: " << buffer << std::endl;
    }

    // 关闭套接字和清理
    closesocket(sock);
    WSACleanup();
}

int main() {
    std::cout << "GPS数据接收客户端启动" << std::endl;

    // 首先设置ADB端口转发
    if (setup_adb_forward()) {
        // 给ADB转发一点时间来生效
        Sleep(1000); // Windows Sleep函数，单位是毫秒
        std::cout << "尝试连接到localhost:54321..." << std::endl;
        connect_to_gps();
    }
    else {
        std::cout << "由于ADB端口转发失败，无法继续执行" << std::endl;
    }

    return 0;
}