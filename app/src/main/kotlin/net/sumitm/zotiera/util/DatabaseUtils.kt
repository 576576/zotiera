package net.sumitm.zotiera.util

import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReadWriteLock
import java.util.concurrent.locks.ReentrantReadWriteLock

/** 数据库访问工具类。 */
object DatabaseUtils {
    @JvmField
    val readWriteLock: ReadWriteLock = ReentrantReadWriteLock()

    @JvmField
    val writeLock: Lock = readWriteLock.writeLock()

    @JvmField
    val readLock: Lock = readWriteLock.readLock()
}
