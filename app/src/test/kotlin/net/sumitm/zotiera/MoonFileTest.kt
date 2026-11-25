package net.sumitm.zotiera

import net.sumitm.zotiera.model.MoonOrbit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卫星文件（`.moon`）文件头验证。
 *
 * .moon 文件是引擎 `World` 的序列化体：第一个 Byte 是文件类型，
 * 接着 8 个 Byte 是 world id。卫星类型 `World::TYPE_MOON = 127`（0x7f），
 * 行星是 0x01。最初卫星页复用了行星的 `bytes[0] != 0x01`，导致任何卫星文件
 * 都 pass 不了、导入被静默吞掉——这个测试把文件类型锁死。
 */
class MoonFileTest {

    private fun worldId(bytes: ByteArray): Long? {
        if (bytes.size < 9) return null
        if (bytes[0] != MoonOrbit.TYPE_MOON.toByte()) return null
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        return worldId
    }

    // 一份长度为 32 的序列化 `World`：type + world id + timestamp + 空 key/signature
    private val moonBlob = byteArrayOf(
        MoonOrbit.TYPE_MOON.toByte(),
        0x11.toByte(), 0x22.toByte(), 0x33.toByte(), 0x44.toByte(),
        0x55.toByte(), 0x66.toByte(), 0x77.toByte(), 0x88.toByte(),
        0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(),
        0.toByte(), 0.toByte(), 0.toByte(), 0.toByte(),
    )

    @Test
    fun rejectsShortOrWrongType() {
        assertEquals(null, worldId(byteArrayOf()))
        assertEquals(null, worldId(byteArrayOf(0x01.toByte())))
        assertEquals(null, worldId(byteArrayOf(Byte.MAX_VALUE.toByte())))
    }

    @Test
    fun parsesMoonWorldId() {
        assertEquals(0x1122334455667788L, worldId(moonBlob))
    }

    @Test
    fun engineTypeBytesAreWhoSaid() {
        assertEquals(MoonOrbit.TYPE_MOON.toByte(), 0x7f.toByte())
        assertEquals(MoonOrbit.TYPE_PLANET.toByte(), 0x01.toByte())
    }

    @Test
    fun planetDoesNotPassMoonValidation() {
        assertEquals(null, worldId(byteArrayOf(0x01.toByte(), 0.toByte(), 0.toByte())))
    }
}
