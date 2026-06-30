package com.ai.assistance.operit.terminal.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * Termux/ZeroTermux 工具类
 *
 * 负责检测 Termux 安装状态、获取路径、构建 Intent 等。
 */
object TermuxUtils {
    private const val TAG = "TermuxUtils"

    /** Termux 原生包名 */
    const val TERMUX_PACKAGE = "com.termux"

    /** ZeroTermux 包名（功能增强版） */
    const val ZEROTERMUX_PACKAGE = "com.termux.zp"

    /** Termux 内部的 usr 路径 */
    const val TERMUX_USR_PATH = "/data/data/com.termux/files/usr"

    /** Termux 内部的 home 路径 */
    const val TERMUX_HOME_PATH = "/data/data/com.termux/files/home"

    /** ZeroTermux 内部的 usr 路径 */
    const val ZEROTERMUX_USR_PATH = "/data/data/com.termux.zp/files/usr"

    /** ZeroTermux 内部的 home 路径 */
    const val ZEROTERMUX_HOME_PATH = "/data/data/com.termux.zp/files/home"

    /** Termux 的 bash 路径 */
    const val TERMUX_BASH = "$TERMUX_USR_PATH/bin/bash"

    /** ZeroTermux 的 bash 路径 */
    const val ZEROTERMUX_BASH = "$ZEROTERMUX_USR_PATH/bin/bash"

    /**
     * 检测 Termux 是否已安装
     */
    fun isTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检测 ZeroTermux 是否已安装
     */
    fun isZeroTermuxInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(ZEROTERMUX_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * 检测任意一种 Termux 是否已安装
     * @return 检测到的包名，未安装返回 null
     */
    fun detectInstalledTermux(context: Context): String? {
        return when {
            isTermuxInstalled(context) -> {
                Log.d(TAG, "检测到 Termux (com.termux)")
                TERMUX_PACKAGE
            }
            isZeroTermuxInstalled(context) -> {
                Log.d(TAG, "检测到 ZeroTermux (com.termux.zp)")
                ZEROTERMUX_PACKAGE
            }
            else -> {
                Log.d(TAG, "未检测到 Termux 或 ZeroTermux")
                null
            }
        }
    }

    /**
     * 根据包名获取对应的 bash 路径
     */
    fun getBashPath(packageName: String): String {
        return when (packageName) {
            TERMUX_PACKAGE -> TERMUX_BASH
            ZEROTERMUX_PACKAGE -> ZEROTERMUX_BASH
            else -> TERMUX_BASH
        }
    }

    /**
     * 根据包名获取对应的 home 目录
     */
    fun getHomePath(packageName: String): String {
        return when (packageName) {
            TERMUX_PACKAGE -> TERMUX_HOME_PATH
            ZEROTERMUX_PACKAGE -> ZEROTERMUX_HOME_PATH
            else -> TERMUX_HOME_PATH
        }
    }

    /**
     * 根据包名获取对应的 usr 路径
     */
    fun getUsrPath(packageName: String): String {
        return when (packageName) {
            TERMUX_PACKAGE -> TERMUX_USR_PATH
            ZEROTERMUX_PACKAGE -> ZEROTERMUX_USR_PATH
            else -> TERMUX_USR_PATH
        }
    }

    /**
     * 构建 Termux RUN_COMMAND Intent
     *
     * 用于静默执行命令（一次性返回结果，不支持流式输出）
     *
     * @param packageName Termux 包名
     * @param command 要执行的命令
     * @param workDir 工作目录（可选）
     * @param background 是否后台执行
     */
    fun createRunCommandIntent(
        packageName: String,
        command: String,
        workDir: String? = null,
        background: Boolean = false
    ): Intent {
        return Intent("com.termux.RUN_COMMAND").apply {
            `package` = packageName
            putExtra("com.termux.RUN_COMMAND_PATH",
                arrayOf("/data/data/$packageName/files/usr/bin/bash", "-c", command))
            workDir?.let {
                putExtra("com.termux.RUN_COMMAND_WORKDIR", it)
            }
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }
    }

    /**
     * 获取 Termux 的环境变量映射
     *
     * @param packageName Termux 包名
     */
    fun getEnvironment(packageName: String): Map<String, String> {
        val home = getHomePath(packageName)
        val usr = getUsrPath(packageName)
        return mapOf(
            "HOME" to home,
            "SHELL" to "/data/data/$packageName/files/usr/bin/bash",
            "TERMUX_APP__PACKAGE_NAME" to packageName,
            "TERMUX_VERSION" to "",
            "PREFIX" to usr,
            "LD_LIBRARY_PATH" to "$usr/lib",
            "PATH" to "$usr/bin:$usr/bin/applets:/system/bin:/system/xbin",
            "LANG" to "en_US.UTF-8",
            "TMPDIR" to "/data/data/$packageName/files/tmp"
        )
    }
}
