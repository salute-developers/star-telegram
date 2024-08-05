package ru.sberdevices.sbdv.util

import android.os.Build
import org.telegram.messenger.ApplicationLoader

enum class DeviceType(val productName: String) {
    SBERBOX_TOP("satellite");
}

enum class VendorType(val productName: String) {
    HUAWEI("huawei"),
    SBERDEVICES("SberDevices"),
    OTHER("other");
}

object DeviceUtils {

    private val is320Dpi = ApplicationLoader.applicationContext.resources.displayMetrics.densityDpi == 320

    private val is160Dpi = ApplicationLoader.applicationContext.resources.displayMetrics.densityDpi == 160

    @JvmStatic
    fun is320Dpi(): Boolean = is320Dpi

    @JvmStatic
    fun is160Dpi(): Boolean = is160Dpi

    @JvmStatic
    fun isHuawei(): Boolean = getVendorType() == VendorType.HUAWEI

    @JvmStatic
    fun isSberDevices(): Boolean = getVendorType() == VendorType.SBERDEVICES

    @JvmStatic
    fun isSberBoxTop(): Boolean = getProductName().startsWith(DeviceType.SBERBOX_TOP.productName)

    @JvmStatic
    fun getVendorType(): VendorType =
        VendorType.values().firstOrNull { it.productName.equals(getVendorName(), ignoreCase = true) }
            ?: VendorType.OTHER

    @JvmStatic
    fun getProductName(): String = Build.PRODUCT

    @JvmStatic
    fun getVendorName(): String = Build.MANUFACTURER
}
