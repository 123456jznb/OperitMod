package com.ai.assistance.operit.terminal.provider.type

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.terminal.TerminalSession
import com.ai.assistance.operit.terminal.provider.filesystem.FileSystemProvider
import com.ai.assistance.operit.terminal.provider.filesystem.LocalFileSystemProvider
import com.ai.assistance.operit.terminal.utils.TermuxUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

/**
 * Termux/ZeroTermux 终端提供者
 *
 * 通过启动 Termux 的 bash 进程来提供终端能力。
 * 支持两种执行模式：
 * 1. 交互式终端（startSession）：启动 bash --login，通过 Process I/O 通信
 * 2. 静默执行（executeHiddenCommand）：启动 bash -c，等待完成返回结果
 *
 * 自动检测 Termux 或 ZeroTermux，优先使用已安装的。
 */
class TermuxTerminalProvider(
    private val context: Context
) : TerminalProvider {

    companion object {
        private const val TAG = "TermuxTerminalProvider"
    }

    /** 检测到的 Termux 包名，可能为 null（未安装） */
    private val termuxPackage: String? = TermuxUtils.detectInstalledTermux(context)

    /** 文件系统提供者——直接操作 Android 文件系统（Termux 路径就是真实路径） */
    private val fileSystemProvider: FileSystemProvider = LocalFileSystemProvider()

    /** Provider 是否已连接 */
    private var connected: Boolean = false

    override suspend fun isConnected(): Boolean {
        return connected && termuxPackage != null
    }

    override suspend fun connect(): Result<Unit> {
        return withContext(Dispatchers.IO) {
            val pkg = termuxPackage
            if (pkg == null) {
                Log.e(TAG, "连接失败：未检测到 Termux 或 ZeroTermux")
                connected = false
                return@withContext Result.failure(Exception(
                    "Termux not installed. Please install Termux or ZeroTermux first."
                ))
            }

            // 验证 Termux 的 bash 是否存在
            val bashPath = TermuxUtils.getBashPath(pkg)
            val bashFile = File(bashPath)
            if (!bashFile.exists()) {
                Log.e(TAG, "连接失败：Termux bash 不存在 ($bashPath)")
                connected = false
                return@withContext Result.failure(Exception(
                    "Termux bash not found at $bashPath. Please check your Termux installation."
                ))
            }

            Log.d(TAG, "TermuxTerminalProvider 连接成功 (package=$pkg, bash=$bashPath)")
            connected = true
            Result.success(Unit)
        }
    }

    override suspend fun disconnect() {
        connected = false
        Log.d(TAG, "TermuxTerminalProvider 已断开")
    }

    /**
     * 启动交互式终端会话
     *
     * 直接启动 Termux 的 bash --login 进程，通过 Process 的 I/O 流通信。
     * 不依赖 PTY，但足够支持终端交互。
     */
    override suspend fun startSession(sessionId: String): Result<Pair<TerminalSession, com.ai.assistance.operit.terminal.Pty>> {
        val pkg = termuxPackage
        if (pkg == null) {
            return Result.failure(Exception("Termux not installed"))
        }

        return withContext(Dispatchers.IO) {
            try {
                val bashPath = TermuxUtils.getBashPath(pkg)
                val homePath = TermuxUtils.getHomePath(pkg)
                val env = TermuxUtils.getEnvironment(pkg)

                val processBuilder = ProcessBuilder()
                    .command(bashPath, "--login")
                    .directory(File(homePath))
                    .redirectErrorStream(false)

                // 设置环境变量
                processBuilder.environment().putAll(env)

                val process = processBuilder.start()

                val session = TerminalSession(
                    process = process,
                    stdout = process.inputStream,
                    stdin = process.outputStream
                )

                Log.d(TAG, "Termux 交互式终端会话已启动 (session=$sessionId, pid=${process.pid()})")

                // Termux 模式不需要 PTY，用 null 替代
                Result.success(Pair(session, null))
            } catch (e: Exception) {
                Log.e(TAG, "启动 Termux 会话失败", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun closeSession(sessionId: String) {
        // SessionManager 会处理 process 的关闭
        Log.d(TAG, "关闭 Termux 会话 (session=$sessionId)")
    }

    /**
     * 静默执行命令
     *
     * 方式一（推荐）：通过 ProcessBuilder 直接执行 bash -c
     * 方式二（备选）：通过 Termux RUN_COMMAND Intent
     */
    override suspend fun executeHiddenCommand(
        command: String,
        executorKey: String,
        timeoutMs: Long
    ): HiddenExecResult {
        val pkg = termuxPackage
        if (pkg == null) {
            return HiddenExecResult(
                output = "",
                exitCode = -1,
                state = HiddenExecResult.State.SHELL_START_FAILED,
                error = "Termux not installed"
            )
        }

        return withContext(Dispatchers.IO) {
            try {
                val bashPath = TermuxUtils.getBashPath(pkg)
                val homePath = TermuxUtils.getHomePath(pkg)
                val env = TermuxUtils.getEnvironment(pkg)

                val processBuilder = ProcessBuilder()
                    .command(bashPath, "-c", command)
                    .directory(File(homePath))
                    .redirectErrorStream(true)

                processBuilder.environment().putAll(env)

                val process = processBuilder.start()

                // 读取输出
                val output = process.inputStream.bufferedReader().use { it.readText() }

                // 等待完成（含超时）
                val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    Log.w(TAG, "命令超时: $command")
                    return@withContext HiddenExecResult(
                        output = output,
                        exitCode = -1,
                        state = HiddenExecResult.State.TIMEOUT,
                        error = "Command timed out after ${timeoutMs}ms"
                    )
                }

                val exitCode = process.exitValue()
                Log.d(TAG, "Termux 命令完成 (exitCode=$exitCode)")

                HiddenExecResult(
                    output = output,
                    exitCode = exitCode,
                    state = if (exitCode == 0) HiddenExecResult.State.OK else HiddenExecResult.State.EXECUTION_ERROR,
                    error = if (exitCode != 0) "Command exited with code $exitCode" else ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Termux 命令执行失败", e)
                HiddenExecResult(
                    output = "",
                    exitCode = -1,
                    state = HiddenExecResult.State.EXECUTION_ERROR,
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    override fun getFileSystemProvider(): FileSystemProvider {
        return fileSystemProvider
    }

    override suspend fun getWorkingDirectory(): String {
        return termuxPackage?.let { TermuxUtils.getHomePath(it) } ?: "/"
    }

    override fun getEnvironment(): Map<String, String> {
        return termuxPackage?.let { TermuxUtils.getEnvironment(it) } ?: emptyMap()
    }
}