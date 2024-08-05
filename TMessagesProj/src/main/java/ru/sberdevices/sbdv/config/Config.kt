package ru.sberdevices.sbdv.config

import android.content.Context
import android.util.Log
import androidx.annotation.AnyThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import org.json.JSONException
import org.json.JSONObject
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.BuildVars
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.sbdv.SbdvServiceLocator
import ru.sberdevices.sbdv.util.DeviceUtils
import ru.sberdevices.smartfocus.SmartFocusMode

private val SMART_FOCUS_MODES = mapOf(
    "Disabled" to SmartFocusMode.Disabled,
    "Follow.Everyone" to SmartFocusMode.Follow.Everyone,
    "Follow.EveryoneFaceOnly" to SmartFocusMode.Follow.EveryoneFaceOnly,
    "Follow.PersonsWithFaceAppeared" to SmartFocusMode.Follow.PersonsWithFaceAppeared
)

/**
 * Класс содержит конфигурируемые через конфиг настройки приложения.
 *
 * [voipProtocolVersion] - Версия протокола voip
 * [voipFeedbackDisplayProbability] - Вероятность появление экрана оценки качества связи
 * [voipFeedbackDisplayingControlledByTelegram] - Оценивается ли качество связи дуровским телеграмом или нашим дополнительным кодом
 * [transliterationEnabled] - Включена ли транслитерация имен контактов.
 * [voipH264Enabled] - включен ли H264 кодек
 * [voipH265Enabled] - включен ли H265 кодек
 * [smartFocusTargetEnabled] - включает специальный решим смартфокуса, когда он фокусируется на одном человеке
 * [smartFocusMode] - Режим смартфокуса
 * [qrCodeLoginEnabled] - Включен ли вход в Телеграм через QR
 * [integratedWithSingleCallsPlace] - Включена ли интеграция с Единым Звонковым Приложением на СтарОС 1.74
 * [integratedWithSingleCallsPlace175] - Включена ли интеграция ЕЗП на СтарОС 1.75
 * [integratedWithSingleCallsPlace176] - Включена ли интеграция ЕЗП начиная со СтарОС 1.76 и выше
 * [prudentNetworking] - Оптимизация сетевых запросов
 * [avoidTelegramNotifications] - Отключение стандартных телеграммных нотификаций о сообщениях из чатов, каналов, переписок, пропущенных вызовов и т.п.
 * [logsEnabled] - Включены ли дополнительные логи (связанные, в основном, с первоначальным тележным легаси и которые могут засорять старосный лог)
 * [packagesAllowedToAccessAvatars] - Приложения которые имеют доступ к аватарам контактов
 * [contactsForceRefreshIntervalMinutes] - Форсированное обновление контактов через заданный промежуток времени
 * [isReducedRegistrationEnabled] - Включает урезанный флоу подключения аккаунта - без регистрации, восстановления пароля и сброса аккаунта
 * [disableNoConnectionAlertDuringUpdating] - При начале звонка, если подключение к сети есть, но происходит getDifference(), не показывать алерт об отсутствии сети
 *
 * Актуально на 05.05.2022
 */

// It's effectively a singleton, so we don't want to close resources
@AnyThread
class Config(private val context: Context, coroutineDispatchers: CoroutineDispatchers) {

    private val scope = CoroutineScope(SupervisorJob() + coroutineDispatchers.io)

    private var _avoidTelegramNotifications = true
    private var _contactsForceRefreshIntervalMinutes = 0L
    private var _forceHdmiEnabled = false
    private var _integratedWithSingleCallsPlace = false
    private var _packagesAllowedToAccessAvatars = emptySet<String>()
    private var _prudentNetworking = false
    private var _qrCodeLoginEnabled = false
    private var _smartFocusMode: SmartFocusMode = SmartFocusMode.Follow.EveryoneFaceOnly
    private var _smartFocusTargetEnabled = false
    private var _starNotificationsEnabled = false
    private var _transliterationEnabled: Boolean = true
    private var _voipFeedbackDisplayProbability: Float = 0f
    private var _voipFeedbackDisplayingControlledByTelegram: Boolean = true
    private var _voipH264Enabled = DeviceUtils.isHuawei()
    private var _voipH265Enabled = true
    private var _voipProtocolVersion: Int = 0
    private var _newBackgroundEnabled: Boolean = false
    private var _reducedRegistrationEnabled: Boolean = true
    private var _disableNoConnectionAlertDuringUpdating: Boolean = false

    private val applicationConfigProvider by lazy {
        try {
            AppConfigProviderImpl(context.applicationContext, coroutineDispatchers)
        } catch (t: Throwable) {
            Log.w(TAG, "AppConfigProviderStub created instead of a real one")
            AppConfigProviderStub()
        }
    }

    private val configChangesFlow by lazy {
        applicationConfigProvider.configFlow
            .distinctUntilChanged()
            .map { config -> config.parseToJsonObject() }
            .flowOn(coroutineDispatchers.io)
    }

    init {
        Log.d(TAG, "<init>@" + hashCode() + ". " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")")

        combine(
            applicationConfigProvider.configFlow
                .distinctUntilChanged()
                .map { jsonString -> jsonString.parseToJsonObject() },
            SbdvServiceLocator.getFirmwareVersionRepositorySharedInstance().versionName
        ) { config, versionName -> config to versionName }
            .distinctUntilChanged()
            .onEach { (config, versionName) ->
                synchronized(this@Config) {
                    _voipProtocolVersion = config.optInt("voipProtocolVersion")
                    _voipFeedbackDisplayProbability =
                        config.optDouble(KEY_VOIP_FEEDBACK_DISPLAY_PROBABILITY, .0).toFloat()
                    _voipFeedbackDisplayingControlledByTelegram =
                        config.optBoolean(KEY_VOIP_FEEDBACK_DISPLAYING_CONTROLLED_BY_TELEGRAM, true)

                    _voipH264Enabled = config.optBoolean("voipH264Enabled", _voipH264Enabled)
                    _voipH265Enabled = config.optBoolean("voipH265Enabled", _voipH265Enabled)
                    _smartFocusTargetEnabled = config.optBoolean("smartFocusTargetEnabled", _smartFocusTargetEnabled)
                    _smartFocusMode =
                        SMART_FOCUS_MODES[config.optString("smartFocusMode")] ?: _smartFocusMode
                    _qrCodeLoginEnabled = config.optBoolean("qrCodeLoginEnabled", _qrCodeLoginEnabled)

                    BuildVars.LOGS_ENABLED = config.optBoolean("logsEnabled", false)

                    _transliterationEnabled = config.optBoolean(KEY_TRANSLITERATION_ENABLED, _transliterationEnabled)

                    _integratedWithSingleCallsPlace = determineIfSingleCallsPlaceIsEnabled(config, versionName)

                    _prudentNetworking = config.optBoolean(KEY_PRUDENT_NETWORKING, _prudentNetworking)

                    _avoidTelegramNotifications =
                        config.optBoolean(KEY_AVOID_TELEGRAM_NOTIFICATIONS, _avoidTelegramNotifications)

                    _starNotificationsEnabled =
                        config.optBoolean(KEY_STAR_NOTIFICATIONS_ENABLED, _starNotificationsEnabled)
                    _packagesAllowedToAccessAvatars = determinePackagesAllowedToAccessAvatars(config)
                    _contactsForceRefreshIntervalMinutes = config.optLong(KEY_CONTACTS_FORCE_REFRESH_INTERVAL_MINUTES)
                    _forceHdmiEnabled = determineIfForceHdmiCanBeEnabled(config, versionName)
                    _newBackgroundEnabled = config.optBoolean(KEY_NEW_BACKGROUND_ENABLED, _newBackgroundEnabled)
                    _reducedRegistrationEnabled =
                        config.optBoolean(KEY_REDUCED_REGISTRATION_ENABLED, _reducedRegistrationEnabled)
                    _disableNoConnectionAlertDuringUpdating = config.optBoolean(KEY_DISABLE_NO_CONNECTION_ALERT_DURING_UPDATING, _disableNoConnectionAlertDuringUpdating)
                }
            }
            .launchIn(scope)
    }

    private fun determinePackagesAllowedToAccessAvatars(config: JSONObject): Set<String> {
        return config
            .optJSONArray(KEY_PACKAGES_ALLOWED_TO_ACCESS_AVATARS)
            ?.let { array ->
                val allowedPackages = mutableSetOf<String>()
                for (i in 0 until array.length()) {
                    val nextAllowedPackage = array.optString(i, null)
                    if (nextAllowedPackage != null) allowedPackages += nextAllowedPackage
                }
                allowedPackages
            }
            ?: _packagesAllowedToAccessAvatars
    }

    val watchPartyEnabledFlow: Flow<Boolean> by lazy {
        configChangesFlow.map { configJson -> configJson.optBoolean(KEY_WATCH_PARTY_ENABLED, false) }
    }

    val integratedWithSingleCallsPlaceFlow: Flow<Boolean> by lazy {
        combine(
            applicationConfigProvider.configFlow
                .distinctUntilChanged()
                .map { jsonString -> jsonString.parseToJsonObject() },
            SbdvServiceLocator.getFirmwareVersionRepositorySharedInstance().versionName
        ) { config, versionName -> config to versionName }
            .distinctUntilChanged()
            .map { (config, versionName) -> determineIfSingleCallsPlaceIsEnabled(config, versionName) }
    }

    val packagesAllowedToAccessAvatarsFlow: Flow<Set<String>> by lazy {
        configChangesFlow
            .map { configJson -> determinePackagesAllowedToAccessAvatars(configJson) }
            .distinctUntilChanged()
    }

    val contactsForceRefreshIntervalMinutesFlow: Flow<Long> by lazy {
        configChangesFlow
            .map { configJson -> configJson.optLong(KEY_CONTACTS_FORCE_REFRESH_INTERVAL_MINUTES) }
            .distinctUntilChanged()
    }

    val newBackgroundEnabledFlow: Flow<Boolean> by lazy {
        configChangesFlow
            .map { configJson -> configJson.optBoolean(KEY_NEW_BACKGROUND_ENABLED, _newBackgroundEnabled) }
            .distinctUntilChanged()
    }

    val voipProtocolVersion: Int
        @Synchronized
        get() = _voipProtocolVersion

    val voipFeedbackDisplayProbability: Float
        @Synchronized
        get() = _voipFeedbackDisplayProbability

    val voipFeedbackDisplayingControlledByTelegram: Boolean
        @Synchronized
        get() = _voipFeedbackDisplayingControlledByTelegram

    val transliterationEnabled: Boolean
        @Synchronized
        get() = _transliterationEnabled

    val voipH264Enabled: Boolean
        @Synchronized
        get() = _voipH264Enabled

    val voipH265Enabled: Boolean
        @Synchronized
        get() = _voipH265Enabled

    val smartFocusTargetEnabled: Boolean
        @Synchronized
        get() = _smartFocusTargetEnabled

    val smartFocusMode: SmartFocusMode
        @Synchronized
        get() = _smartFocusMode

    val qrCodeLoginEnabled: Boolean
        @Synchronized
        get() = _qrCodeLoginEnabled

    val integratedWithSingleCallsPlace: Boolean
        @Synchronized
        get() = _integratedWithSingleCallsPlace

    val starNotificationsEnabled
        @Synchronized
        get() = _starNotificationsEnabled

    val prudentNetworking: Boolean
        @Synchronized
        get() = _prudentNetworking

    val avoidTelegramNotifications: Boolean
        @Synchronized
        get() = _avoidTelegramNotifications

    val packagesAllowedToAccessAvatars: Set<String>
        @Synchronized
        get() = _packagesAllowedToAccessAvatars

    val forceHdmiEnabled: Boolean
        @Synchronized
        get() = _forceHdmiEnabled

    val newBackgroundEnabled: Boolean
        @Synchronized
        get() = _newBackgroundEnabled

    val isReducedRegistrationEnabled: Boolean
        @Synchronized
        get() = _reducedRegistrationEnabled

    val disableNoConnectionAlertDuringUpdating: Boolean
        @Synchronized
        get() = _disableNoConnectionAlertDuringUpdating

    private fun String.parseToJsonObject(): JSONObject {
        Log.d(TAG, "New app config: $this")
        return try {
            JSONObject(this)
        } catch (e: JSONException) {
            Log.e(TAG, "JSONException occurred on parsing application config", e)
            JSONObject()
        }
    }

    private fun determineIfSingleCallsPlaceIsEnabled(config: JSONObject, versionName: String?): Boolean {
        val versionNumber = versionName?.take(4)?.filter { it.isDigit() }?.toIntOrNull()
        // TODO: uncomment when SINGLE_CALLS will me prepared
        // val key = when {
        //     versionNumber == null -> KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE
        //     versionNumber == 175 -> KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE_175
        //     versionNumber > 175 -> KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE_176
        //     else -> null
        // }
        val key = KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE
        val parsedValue = key?.let { config.optBoolean(key, _integratedWithSingleCallsPlace) }
        Log.d(
            TAG, "Firmware version name: $versionName, version number: $versionNumber, " +
            "look for $key key. Parsed $parsedValue"
        )
        return parsedValue ?: _integratedWithSingleCallsPlace
    }

    private fun determineIfForceHdmiCanBeEnabled(config: JSONObject, versionName: String?): Boolean {
        val versionNumber = versionName?.take(4)?.filter { it.isDigit() }?.toIntOrNull()
        val key = when {
            versionNumber == null -> KEY_FORCE_HDMI_ENABLED_PRE_179
            versionNumber < 179 -> KEY_FORCE_HDMI_ENABLED_PRE_179
            versionNumber >= 179 -> KEY_FORCE_HDMI_ENABLED
            else -> null
        }
        val parsedValue = key?.let { config.optBoolean(key, _forceHdmiEnabled) }
        Log.d(
            TAG, "Firmware version name: $versionName, version number: $versionNumber, " +
            "look for $key key. Parsed $parsedValue"
        )
        return parsedValue ?: _forceHdmiEnabled
    }

    private companion object {
        const val TAG = "Config"
        const val IS_SBERDEVICE = true
        private const val KEY_WATCH_PARTY_ENABLED = "watchPartyEnabled"
        private const val KEY_VOIP_FEEDBACK_DISPLAY_PROBABILITY = "voipFeedbackDisplayProbability"
        private const val KEY_VOIP_FEEDBACK_DISPLAYING_CONTROLLED_BY_TELEGRAM =
            "voipFeedbackDisplayingControlledByTelegram"
        private const val KEY_TRANSLITERATION_ENABLED = "transliterationEnabled"
        private const val KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE = "integratedWithSingleCallsPlace"
        private const val KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE_175 = "integratedWithSingleCallsPlace175"
        private const val KEY_INTEGRATED_WITH_SINGLE_CALLS_PLACE_176 = "integratedWithSingleCallsPlace176"
        private const val KEY_PRUDENT_NETWORKING = "prudentNetworking"
        private const val KEY_AVOID_TELEGRAM_NOTIFICATIONS = "avoidTelegramNotifications"
        private const val KEY_STAR_NOTIFICATIONS_ENABLED = "starNotificationsEnabled"
        private const val KEY_CONTACTS_FORCE_REFRESH_INTERVAL_MINUTES = "contactsForceRefreshIntervalMinutes"
        private const val KEY_PACKAGES_ALLOWED_TO_ACCESS_AVATARS = "packagesAllowedToAccessAvatars"
        private const val KEY_FORCE_HDMI_ENABLED_PRE_179 = "forceHdmiEnabledPre179"
        private const val KEY_FORCE_HDMI_ENABLED = "forceHdmiEnabled"
        private const val KEY_NEW_BACKGROUND_ENABLED = "newBackgroundEnabled"
        private const val KEY_REDUCED_REGISTRATION_ENABLED = "isReducedRegistrationEnabled"
        private const val KEY_DISABLE_NO_CONNECTION_ALERT_DURING_UPDATING = "disableNoConnectionAlertDuringUpdating"
    }
}
