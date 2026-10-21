package net.sumitm.zotiera.service

import android.util.Log
import com.zerotier.sdk.DataStoreGetListener
import com.zerotier.sdk.DataStorePutListener
import net.sumitm.zotiera.util.Constants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter

/**
 * ZeroTier 的文件数据源。
 *
 * 文件都落在实例目录里（见 `InstanceStore`）；自定义 planet 不走文件，直接替换「planet」这个名字的读写，
 * 避免引擎把用户给的 planet 覆盖掉。
 */
class DataStore(
    private val baseDir: File,
    private val planetOverride: File?,
) : DataStoreGetListener, DataStorePutListener {

    private fun isPlanet(name: String): Boolean =
        planetOverride != null && Constants.FILE_PLANET == name

    override fun onDataStorePut(name: String, buffer: ByteArray, secure: Boolean): Int {
        Log.d(TAG, "Writing File: $name, to: $baseDir")
        if (isPlanet(name)) {
            return 0
        }
        return try {
            val file = File(baseDir, name)
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            FileOutputStream(file).use { out ->
                out.write(buffer)
                out.flush()
            }
            0
        } catch (e: IOException) {
            val writer = StringWriter()
            e.printStackTrace(PrintWriter(writer))
            Log.e(TAG, writer.toString())
            -2
        }
    }

    override fun onDelete(name: String): Int {
        Log.d(TAG, "Deleting File: $name")
        if (isPlanet(name)) {
            return 0
        }
        val file = File(baseDir, name)
        if (!file.exists()) {
            return 0
        }
        return if (file.delete()) 0 else 1
    }

    override fun onDataStoreGet(name: String, outBuffer: ByteArray): Long {
        Log.d(TAG, "Reading File: $name")
        val file = if (isPlanet(name)) planetOverride!! else File(baseDir, name)
        if (!file.exists()) {
            return 0
        }
        return try {
            FileInputStream(file).use { input -> input.read(outBuffer).toLong() }
        } catch (e: IOException) {
            Log.e(TAG, "", e)
            -2
        }
    }

    private companion object {
        const val TAG = "DataStore"
    }
}
