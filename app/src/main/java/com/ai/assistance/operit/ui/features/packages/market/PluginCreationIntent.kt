package com.ai.assistance.operit.ui.features.packages.market

sealed interface PluginCreationIntent {
    val requirement: String

    fun toPrompt(): String

    data class Fresh(
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(): String {
            return buildCreationPrompt(
                taskLine = "请你激活operit editor，并使用沙盒包开发 dev 工具包开发新的沙盒包。",
                packageRuleLine = "先确定新的沙盒包 id，后续不要改名。",
                devDirectoryLine = "开发目录固定为 手机下载/Operit/dev_package/你确定的id。开发、安装和测试都只在这里完成。",
                requirement = requirement
            )
        }
    }

    data class Continue(
        val runtimePackageId: String,
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(): String {
            return buildCreationPrompt(
                taskLine = "请你激活operit editor，查找沙盒包 ${runtimePackageId} 的位置，在此版本基础上继续开发并测试。",
                packageRuleLine = "当前沙盒包 id 是 ${runtimePackageId}。包 id 和插件名字都必须沿用，不要改名，也不要新起包。",
                devDirectoryLine = "开发目录固定为 手机下载/Operit/dev_package/${runtimePackageId}。开发、安装和测试都只在这里完成。",
                requirement = requirement
            )
        }
    }

    data class Merge(
        val runtimePackageId: String,
        override val requirement: String
    ) : PluginCreationIntent {
        override fun toPrompt(): String {
            return buildCreationPrompt(
                taskLine = "请你激活operit editor，查找沙盒包 ${runtimePackageId} 的位置，在此版本基础上做合并开发并测试。",
                packageRuleLine = "当前沙盒包 id 是 ${runtimePackageId}。包 id 和插件名字都必须沿用，不要改名，也不要新起包。",
                devDirectoryLine = "开发目录固定为 手机下载/Operit/dev_package/${runtimePackageId}。开发、安装和测试都只在这里完成。",
                requirement = requirement
            )
        }
    }
}

private fun buildCreationPrompt(
    taskLine: String,
    packageRuleLine: String,
    devDirectoryLine: String,
    requirement: String
): String {
    return buildString {
        appendLine(taskLine)
        appendLine("先更新 SandboxPackage_DEV。")
        appendLine(devDirectoryLine)
        appendLine(packageRuleLine)
        appendLine("把 skills 里的 types 覆盖复制到开发目录。")
        appendLine("尽量用终端，优先写 ts 和 js，再编译出最终 js。tsconfig 参考 examples。")
        appendLine("示范做两个，第二个用自定义布局。")
        appendLine("需求:")
        append(requirement.trim())
    }
}
