package ua.pp.prema.storog.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import ua.pp.prema.storog.R

data class PreflightResult(
    val canRun: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

class PreflightChecker(private val context: Context) {

    fun check(): PreflightResult {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 1. ABI
        val abis = Build.SUPPORTED_ABIS.toList()
        if (!abis.contains("arm64-v8a")) {
            errors.add(context.getString(
                R.string.preflight_error_abi, abis.joinToString()
            ))
        }

        // 2. Android version
        if (Build.VERSION.SDK_INT < 28) {
            errors.add(context.getString(
                R.string.preflight_error_android, Build.VERSION.RELEASE
            ))
        }

        // 3. RAM
        val actMgr  = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo().also { actMgr.getMemoryInfo(it) }
        val ramGb   = memInfo.totalMem.toDouble() / GiB
        val ramStr  = "%.1f GB".format(ramGb)
        val minRamStr = "%.1f GB".format(PreflightLimits.MIN_RAM_GB)
        val recRamStr = "%.1f GB".format(PreflightLimits.RECOMMENDED_RAM_GB)
        when {
            ramGb < PreflightLimits.MIN_RAM_GB -> errors.add(context.getString(R.string.preflight_error_ram, ramStr, minRamStr))
            ramGb < PreflightLimits.RECOMMENDED_RAM_GB -> warnings.add(context.getString(R.string.preflight_warn_ram, ramStr, recRamStr))
        }

        val stat    = StatFs(context.filesDir.absolutePath)
        val freeGb  = (stat.availableBlocksLong * stat.blockSizeLong).toDouble() / GiB
        val freeStr = "%.1f GB".format(freeGb)
        val minStorageStr = "%.1f GB".format(PreflightLimits.MIN_STORAGE_GB)
        val recStorageStr = "%.1f GB".format(PreflightLimits.RECOMMENDED_STORAGE_GB)
        when {
            freeGb < PreflightLimits.MIN_STORAGE_GB -> errors.add(context.getString(R.string.preflight_error_storage, freeStr, minStorageStr))
            freeGb < PreflightLimits.RECOMMENDED_STORAGE_GB -> warnings.add(context.getString(R.string.preflight_warn_storage, freeStr, recStorageStr))
        }

        Log.d(TAG, "ABIs=$abis  RAM=$ramStr  Free=$freeStr  SDK=${Build.VERSION.SDK_INT}")
        return PreflightResult(canRun = errors.isEmpty(), errors = errors, warnings = warnings)
    }

    companion object {
        private const val TAG = "PreflightChecker"
        private const val GiB = 1024.0 * 1024 * 1024
    }
}
