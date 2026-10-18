package net.sumitm.zotiera.data

import androidx.room.TypeConverter
import net.sumitm.zotiera.model.AssignedAddress

/**
 * Room 的类型转换。枚举按原始 int 存，避免改名后语义漂移。
 */
class Converters {

    @TypeConverter
    fun addressTypeToInt(type: AssignedAddress.AddressType?): Int? = type?.id

    @TypeConverter
    fun intToAddressType(value: Int?): AssignedAddress.AddressType? =
        value?.let { AssignedAddress.AddressType.fromInt(it) }
}
