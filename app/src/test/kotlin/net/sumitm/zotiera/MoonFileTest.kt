package net.sumitm.zotiera

import net.sumitm.zotiera.model.MoonOrbit
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卫星文件（`.moon`）文件头验证与根身份地址提取。
 *
 * .moon 文件是引擎 `World` 的序列化体：第一个 Byte 是文件类型，接着 8 个 Byte 是 world id，
 * 然后是时间戳、更新必需的公钥、签名、根数量，之后每个根的 `Identity` 前 5 个 Byte 是大端 ZT
 * 地址。引擎 `Topology::addMoon` 必须拿到卫星根的身份地址作为 seed 才会把卫星加入 `Topology::_moons`，
 * 结点列表的角色才会显示为 `ZT_PEER_ROLE_MOON`（月结点）。
 */
class MoonFileTest {

    private fun worldId(bytes: ByteArray): Long? {
        if (bytes.size < 9) return null
        if (bytes[0] != MoonOrbit.TYPE_MOON.toByte()) return null
        var worldId = 0L
        for (i in 1..8) worldId = (worldId shl 8) + (bytes[i].toLong() and 0xff)
        return worldId
    }

    /** 卫星根的 ZT 地址，按照 `Identity` 序列化格式的前 5 Byte 解析。 */
    private fun rootAddress(bytes: ByteArray): Long? = MoonOrbit.moonRootZtAddress(bytes)

    // 一份完整的序列化 `World`：type + world id + timestamp + updatesMustBeSignedBy(32) +
    // signature(64) + numRoots(1) + root0 Identity（地址 5 + 类型 1 + 公钥 32）= 152 Byte
    private val moonBlob = buildMoonBlob(0x1122334455667788L, 0x123456789AL)

    @Test
    fun rejectsShortOrWrongType() {
        assertEquals(null, worldId(byteArrayOf()))
        assertEquals(null, worldId(byteArrayOf(0x01.toByte())))
        assertEquals(null, worldId(byteArrayOf(Byte.MAX_VALUE.toByte())))
        assertEquals(null, rootAddress(byteArrayOf()))
        assertEquals(null, rootAddress(ByteArray(114) { 0 }))
    }

    @Test
    fun parsesMoonWorldId() {
        assertEquals(0x1122334455667788L, worldId(moonBlob))
    }

    @Test
    fun rootIdentityAddressParsedFromSerialization() {
        // 152 Byte 的卫星：类型 1 + id 8 + 时间戳 8 + 公钥 32 + 签名 64 + 根数量 1 = 114
        assertEquals(152, moonBlob.size)
        // 根数量 Byte 在哪里
        assertEquals(1L, moonBlob[113].toLong() and 0xFFL)
        assertEquals(0x123456789AL, rootAddress(moonBlob))
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

private fun buildMoonBlob(worldId: Long, address: Long): ByteArray {
    // type + world id + timestamp + updatesMustBeSignedBy(32) + signature(64) + numRoots(1)
    val buf = byteArrayOf(MoonOrbit.TYPE_MOON.toByte()) + worldIdToBytes(worldId) + timestampBytes() +
        bytes32(0x01) + bytes64(0x02) + byteArrayOf(1.toByte())
    // root0 Identity: 地址(5, 大端) + 类型(1) + 公钥(32)
    val root = addressToBytes(address) + byteArrayOf(0.toByte()) + bytes32(0x03)
    return buf + root
}

private fun worldIdToBytes(worldId: Long): ByteArray = ByteArray(8).apply {
    for (i in 0 until 8) this[i] = ((worldId shr (64 - 8 * (i + 1))) and 0xff).toByte()
}

private fun timestampBytes(): ByteArray = ByteArray(8) { 0 }

private fun bytes32(fill: Int): ByteArray = ByteArray(32) { fill.toByte() }

private fun bytes64(fill: Int): ByteArray = ByteArray(64) { fill.toByte() }

private fun addressToBytes(address: Long): ByteArray = ByteArray(MoonOrbit.ZT_ADDRESS_LEN).apply {
    for (i in 0 until MoonOrbit.ZT_ADDRESS_LEN) {
        this[i] = ((address shr (MoonOrbit.ZT_ADDRESS_LEN * 8 - 8 * (i + 1))) and 0xff).toByte()
    }
}
