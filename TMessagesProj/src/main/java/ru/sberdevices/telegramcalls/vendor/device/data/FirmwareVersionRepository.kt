package ru.sberdevices.telegramcalls.vendor.device.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.sberdevices.services.device.info.DeviceInfoRepository

/**
 * @author Ирина Карпенко on 12.11.2021
 */
interface FirmwareVersionRepository {
    val versionName: Flow<String?>
    val isAlwaysNightInDreaming: Boolean
}

internal class FirmwareVersionRepositoryImpl(
    private val deviceInfoRepository: DeviceInfoRepository
) : FirmwareVersionRepository {

    override val versionName = deviceInfoRepository.deviceInfoFlow.map { deviceInfo -> deviceInfo.version }

    override val isAlwaysNightInDreaming: Boolean
        get() {
            // val versionName = deviceInfoRepository.deviceInfoFlow.value?.version
            // val versionNumber = versionName?.take(4)?.filter { it.isDigit() }?.toIntOrNull()
            // return versionNumber == 174 && !DeviceUtils.isHuawei() && !DeviceUtils.isSberBoxTop()
            return false
        }
}