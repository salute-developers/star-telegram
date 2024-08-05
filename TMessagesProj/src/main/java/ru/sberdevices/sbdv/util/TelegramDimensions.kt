package ru.sberdevices.sbdv.util

import kotlin.math.ceil

/**
 * Class for working with Telegram legacy code.
 *
 * Most Telegram UI was programmed in code that contains a lot of textSize and padding|margin constants.
 * To avoid changing all these programmed constants manually in case, for example, of changing display density we should
 * prefer to use this class to keep these constants and change UI params here.
 * At 17.11.2021 we support only 320 and 160 dpi.
 * @author Anatoliy Gordienko on 17.11.2021
 */
// TODO: вынести дименшены для Логина по аналогии с VoipFragment
object TelegramDimensions {

    @JvmStatic
    val sp12 = calculateSize(12)

    @JvmStatic
    val sp14 = calculateSize(14)

    @JvmStatic
    val sp16 = calculateSize(16)

    @JvmStatic
    val sp20 = calculateSize(20)

    /** Phone Login Dimensions */
    @JvmStatic
    val titleTextSize = sp16

    @JvmStatic
    val selectCountryItemTextSize = selectSize(12, 19)

    @JvmStatic
    val phoneLoginEditTextButtonTextSize = selectSize(14, 24) //Должен быть 14 + semibold

    @JvmStatic
    val confirmTextSize = sp12

    @JvmStatic
    val phoneLoginButtonTitle = sp14

    @JvmStatic
    val subtitleTextSize = calculateSize(15)

    @JvmStatic
    val smallTextSize = selectSize(11, 18)

    /** Theme dimensions */
    @JvmStatic
    val actionBarRoundButtonSize = selectSize(32, 32)

    private fun get160dpiSize(fromValue: Int) = ceil(fromValue * 1.5).toInt()

    /** Считает dp и sp в тележном легаси коде, изначально задавать в нормальном MDPI размере, зачастую совпадает с макетами Figma */
    @JvmStatic
    fun calculateSize(value: Int): Int {
        return if (DeviceUtils.is320Dpi()) value else get160dpiSize(value)
    }

    /** Если нам нужно особое поведение, отличающееся от calculateSize используем эту функцию */
    @JvmStatic
    fun selectSize(valueFor320dpi: Int, valueFor160dpi: Int): Int {
        return if (DeviceUtils.is320Dpi()) valueFor320dpi else valueFor160dpi
    }

    /**
     * Телеграм рассчитывал отступы и размер текста программно в функции AndroidUtilities.dp(), которая в зависимости от плотности экрана
     * умножает значение на соответствующий множитель. При 320dp множетель был 2, из-за чего все выглядело слишком крупным.
     * Стандартный HDPI множитель - 1.5, MDPI - 1
     * Если плотность экрана отличается от 160 или 320 - то считаем стандартным телеграмовским методом
     */
    @JvmStatic
    fun getAndroidUtilitiesDpTransformation(value: Float, density: Float): Int {
        return when {
            DeviceUtils.is320Dpi() -> ceil(1.5 * value).toInt()
            DeviceUtils.is160Dpi() -> ceil(1 * value).toInt()
            else -> ceil(density * value).toInt()
        }
    }
}