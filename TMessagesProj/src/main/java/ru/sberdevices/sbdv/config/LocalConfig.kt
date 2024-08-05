package ru.sberdevices.sbdv.config

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.telegram.messenger.voip.VideoCapturerDevice
import ru.sberdevices.sbdv.SbdvServiceLocator

/**
 * Класс содержит локальные настройки приложения Видеозвонки.
 *
 * [developerModeEnabled] - режим разработчика. Включается нажатием на аватарку 10 раз. Открывает возможность дополнительных настроек приложения.
 * [mockCallsOn] - при нажатии на карточку контакта отображается тост вместо начала звонка
 * [cropSourceResolution] - до какого разрешения обрезаем промежуточное изображение во время видеозвонка, должно быть <= CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE
 * [dewarpByCropEnabled] - улучшение качества обрезанного изображения
 * [captureFps] - FPS видео
 * [avoidH265Encoder] - не использовать H265 видеокодек
 *
 * Актуально на 27.01.2022
 */

data class LocalConfiguration(
    val developerModeEnabled: Boolean,
    val mockCallsOn: Boolean,
    val cropSourceResolution: Size?,
    val dewarpByCropEnabled: Boolean,
    val captureFps: Int,
    val avoidH265Encoder: Boolean,
)

private const val TAG = "LocalConfig"

class LocalConfig(context: Context) {

    private val sharedPreferences =
        context.applicationContext.getSharedPreferences(LOCAL_CONFIG_SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)

    private val localConfigMutableStateFlow = MutableStateFlow(
        readPreferences()
    )
    val localConfigStateFlow: StateFlow<LocalConfiguration> = localConfigMutableStateFlow

    private fun readPreferences(): LocalConfiguration {
        val developerModeEnabled = sharedPreferences.getBoolean(DEVELOPER_MODE_ENABLED_KEY, false)
        val mockCallsOn = sharedPreferences.getBoolean(MOCK_CALL_ON_KEY, false) && developerModeEnabled

        var size = VideoCapturerDevice.DEFAULT_CROP_SOURCE_RESOLUTION;
        if (sharedPreferences.getInt(CROP_SOURCE_WIDTH_KEY, 0) > 0) {
            size = Size(
                sharedPreferences.getInt(CROP_SOURCE_WIDTH_KEY, 0),
                sharedPreferences.getInt(CROP_SOURCE_HEIGHT_KEY, 0)
            )
        }

        val dewarpByCrop = sharedPreferences.getBoolean(DEWARP_BY_CROP_ENABLED_KEY, false)
        val captureFps = sharedPreferences.getInt(CAPTURE_FPS_KEY, VideoCapturerDevice.HAL_CROP_CAPTURE_FPS)
        val avoidH265 = sharedPreferences.getBoolean(AVOID_H265_ENCODER_KEY, !SbdvServiceLocator.config.voipH265Enabled)

        val localConfiguration = LocalConfiguration(
            developerModeEnabled, mockCallsOn, size, dewarpByCrop, captureFps, avoidH265
        )

        Log.d(TAG, "localConfiguration = $localConfiguration")
        return localConfiguration
    }

    fun onEnableDeveloperMode(developerModeEnabled: Boolean) {
        sharedPreferences.edit {
            putBoolean(DEVELOPER_MODE_ENABLED_KEY, developerModeEnabled)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    fun onEnableMockCalls(mockCallsOn: Boolean) {
        sharedPreferences.edit {
            putBoolean(MOCK_CALL_ON_KEY, mockCallsOn)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    fun setCropSourceResolution(resolution: Size) {
        sharedPreferences.edit() {
            putInt(CROP_SOURCE_WIDTH_KEY, resolution.width)
            putInt(CROP_SOURCE_HEIGHT_KEY, resolution.height)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    fun setDewarpByCrop(enabled: Boolean) {
        sharedPreferences.edit() {
            putBoolean(DEWARP_BY_CROP_ENABLED_KEY, enabled)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    fun setCropFps(fps: Int) {
        sharedPreferences.edit() {
            putInt(CAPTURE_FPS_KEY, fps)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    fun setAvoidH265Encoder(avoid: Boolean) {
        sharedPreferences.edit() {
            putBoolean(AVOID_H265_ENCODER_KEY, avoid)
        }
        localConfigMutableStateFlow.value = readPreferences()
    }

    private companion object {
        const val LOCAL_CONFIG_SHARED_PREFERENCES_KEY = "LOCAL_CONFIG_SHARED_PREFERENCES_KEY"
        const val DEVELOPER_MODE_ENABLED_KEY = "DEVELOPER_MODE_ENABLED_KEY"
        const val MOCK_CALL_ON_KEY = "MOCK_CALL_ON_KEY"
        const val CROP_SOURCE_WIDTH_KEY = "CROP_SOURCE_WIDTH_KEY"
        const val CROP_SOURCE_HEIGHT_KEY = "CROP_SOURCE_HEIGHT_KEY"
        const val DEWARP_BY_CROP_ENABLED_KEY = "DEWARP_BY_CROP_ENABLED_KEY"
        const val CAPTURE_FPS_KEY = "CAPTURE_FPS_KEY"
        const val AVOID_H265_ENCODER_KEY = "AVOID_H265_ENCODER_KEY"
    }
}
