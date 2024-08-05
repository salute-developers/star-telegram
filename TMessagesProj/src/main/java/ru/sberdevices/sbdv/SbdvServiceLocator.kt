package ru.sberdevices.sbdv

import android.content.Context
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.telegram.messenger.BuildConfig
import org.telegram.messenger.MessagesController
import org.telegram.messenger.NotificationCenter
import org.telegram.messenger.UserConfig
import org.telegram.tgnet.ConnectionsManager
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchersImpl
import com.sdkit.core.analytics.domain.Analytics.EventParam
import com.sdkit.core.di.platform.Api
import com.sdkit.core.di.platform.ApiProvider
import com.sdkit.core.di.platform.ApiRegistry
import com.sdkit.core.di.platform.ApiResolver
import com.sdkit.core.logging.di.CoreLoggingApi
import com.sdkit.core.logging.domain.DefaultCoreLogger
import com.sdkit.core.logging.domain.LocalLogger
import com.sdkit.core.logging.domain.LogInternals
import com.sdkit.core.logging.domain.LogMessageBuilder
import com.sdkit.core.logging.domain.LogPropertiesResolver
import com.sdkit.core.logging.domain.LogRepo
import com.sdkit.core.logging.domain.LogRepoImpl
import com.sdkit.core.logging.domain.LogWriter
import com.sdkit.core.logging.domain.LogWriterLevel
import com.sdkit.core.logging.domain.LogWriterOptionsResolver
import com.sdkit.core.logging.domain.LoggerFactory
import com.sdkit.core.logging.domain.mocked
import ru.sberdevices.analytics.Analytics
import ru.sberdevices.analytics.AnalyticsFactory
import ru.sberdevices.common.binderhelper.BinderHelperFactory2Impl
import ru.sberdevices.common.logger.Logger
import ru.sberdevices.sbdv.analytics.AnalyticsCollector
import ru.sberdevices.sbdv.analytics.AnalyticsCollectorFactory
import ru.sberdevices.sbdv.appstate.AppStateRepository
import ru.sberdevices.sbdv.network.NetworkRepositoryImpl
import ru.sberdevices.sbdv.auth.remoteapi.AuthRemoteApiImpl
import ru.sberdevices.sbdv.auth.repo.AuthRepository
import ru.sberdevices.sbdv.auth.repo.AuthRepositoryImpl
import ru.sberdevices.sbdv.auth.repo.QrCodeFactoryImpl
import ru.sberdevices.sbdv.calls.CallRepository
import ru.sberdevices.sbdv.camera.CameraAvailabilityHelper
import ru.sberdevices.sbdv.config.Config
import ru.sberdevices.sbdv.config.LocalConfig
import ru.sberdevices.sbdv.contacts.ContactsRepository
import ru.sberdevices.sbdv.mic.MicStateHelper
import ru.sberdevices.sbdv.notifications.StarNotifications
import ru.sberdevices.sbdv.remoteapi.CancelFunction
import ru.sberdevices.sbdv.remoteapi.RequestSenderImpl
import ru.sberdevices.sbdv.remoteapi.SendFunction
import ru.sberdevices.sbdv.util.AnalyticsStub
import ru.sberdevices.sbdv.util.CallManagerStub
import ru.sberdevices.sbdv.util.DreamingEventsReceiver
import ru.sberdevices.sbdv.util.SpotterStateRepositoryStub
import ru.sberdevices.sbdv.util.VoiceQualityEnhancerStub
import ru.sberdevices.sbdv.viewmodel.VoIPModel
import ru.sberdevices.sdk.echocancel.VoiceQualityEnhancer
import ru.sberdevices.sdk.echocancel.VoiceQualityEnhancerFactory
import ru.sberdevices.services.calls.CallManager
import ru.sberdevices.services.calls.CallManagerFactory
import ru.sberdevices.services.device.info.DeviceInfoRepositoryFactory
import ru.sberdevices.services.mic.camera.state.MicCameraStateRepository
import ru.sberdevices.services.mic.camera.state.MicCameraStateRepositoryFactory
import ru.sberdevices.services.notification.v2.StarNotificationManager
import ru.sberdevices.services.notification.v2.StarNotificationManagerFactory
import ru.sberdevices.services.recent.apps.RecentAppsDispatcher
import ru.sberdevices.services.recent.apps.RecentAppsFactory
import ru.sberdevices.services.spotter.state.SpotterStateRepository
import ru.sberdevices.services.spotter.state.SpotterStateRepositoryFactory
import ru.sberdevices.settings.Settings
import ru.sberdevices.settings.SettingsFactory
import ru.sberdevices.starcontacts.api.vendor.calls.StarCallLogDestinationProvider
import ru.sberdevices.starcontacts.api.vendor.contacts.StarContactsDestinationProvider
import ru.sberdevices.telegramcalls.data.hdmi.HdmiRouteEnforcer
import ru.sberdevices.telegramcalls.data.hdmi.HdmiRouteEnforcerImpl
import ru.sberdevices.telegramcalls.vendor.VendorServiceImpl
import ru.sberdevices.telegramcalls.vendor.authorization.data.AuthorizationNotificationsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.authorization.data.AuthorizationRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.authorization.domain.AuthorizationFeature
import ru.sberdevices.telegramcalls.vendor.authorization.domain.AuthorizationFeatureImpl
import ru.sberdevices.telegramcalls.vendor.calls.data.CallsNotificationsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.calls.data.CallsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.calls.domain.CallsFeature
import ru.sberdevices.telegramcalls.vendor.calls.domain.CallsFeatureImpl
import ru.sberdevices.telegramcalls.vendor.contacts.data.ContactsNotificationsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.contacts.data.ContactsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.contacts.domain.ContactsFeature
import ru.sberdevices.telegramcalls.vendor.contacts.domain.ContactsFeatureImpl
import ru.sberdevices.telegramcalls.vendor.device.data.FirmwareVersionRepository
import ru.sberdevices.telegramcalls.vendor.device.data.FirmwareVersionRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.recentapps.data.RecentAppsRepository
import ru.sberdevices.telegramcalls.vendor.recentapps.data.RecentAppsRepositoryImpl
import ru.sberdevices.telegramcalls.vendor.tray.Tray
import ru.sberdevices.telegramcalls.vendor.tray.TrayImpl
import ru.sberdevices.telegramcalls.vendor.user.data.AvatarLoader
import ru.sberdevices.telegramcalls.vendor.user.data.AvatarLoaderImpl

private const val TAG = "SbdvServiceLocator"

/**
 * Self-made simple ServiceLocator instead of using DI
 */
@Suppress("StaticFieldLeak")
object SbdvServiceLocator {

    private val logger = Logger.get("SbdvServiceLocator")

    private lateinit var context: Context

    @JvmStatic
    fun init(context: Context) {
        this.context = context.applicationContext
        dreamingEventsReceiver.startListen()
        getAppStateRepository() // We need to create an instance
        config
        registerApiResolver()
    }

    private fun registerApiResolver() {
        val pair = CoreLoggingApi::class.java as Class<out Api> to (
            ApiProvider {
                object : CoreLoggingApi {
                    override val loggerFactory: LoggerFactory = this@SbdvServiceLocator.loggerFactory
                    override val logRepo: LogRepo
                        get() = TODO("Not yet implemented")
                }
            }
        )
        ApiRegistry.installResolver(
            ApiResolver(
                mapOf(pair),
            ),
        )
    }

    private val analytics: Analytics by lazy {
        try {
            AnalyticsFactory.create(context)
        } catch (t: Throwable) {
            logger.warn { "AnalyticsStub created instead of a real one" }
            AnalyticsStub()
        }
    }

    private val analyticsSdk by lazy { AnalyticsCollectorFactory.getAnalyticsSdk(analytics) }

    private val loggerFactory: LoggerFactory by lazy {
        object : LoggerFactory {
            override fun get(tag: String): LocalLogger {
                return LocalLogger(
                    tag = tag,
                    logInternals = LogInternals(
                        logPropertiesResolver = object : LogPropertiesResolver {
                            override fun resolveLogMode(priority: Int): LoggerFactory.LogMode {
                                return LoggerFactory.LogMode.LOG_ALWAYS
                            }

                            override fun resolveLogLength(priority: Int): Int {
                                return Int.MAX_VALUE
                            }
                        },
                        logRepoMode = { LoggerFactory.LogRepoMode.DISABLED },
                        analytics = object : com.sdkit.core.analytics.domain.Analytics {
                            override fun logEvent(name: String, vararg params: EventParam) = Unit
                            override fun logEventWithParamsList(
                                name: String,
                                params: ArrayList<EventParam>,
                            ) = Unit

                            override fun logMessage(message: String) = Unit
                            override fun logError(error: Throwable) = Unit
                            override fun setUserProperty(key: String, value: String) = Unit
                        },
                        logRepo = LogRepoImpl(coroutineDispatchers),
                        coreLogger = DefaultCoreLogger(),
                        logPrefix = LoggerFactory.Prefix("TelegramCalls"),
                        logWriter = object :
                            LogWriter {
                            override fun writeLog(
                                tag: String,
                                message: String,
                                th: Throwable?,
                                logWriterLevel: LogWriterLevel,
                            ) = Unit
                        },
                        logWriterOptionsResolver = object : LogWriterOptionsResolver {
                            override fun resolveAppLogWriterMode(): LoggerFactory.LogWriterMode {
                                return LoggerFactory.LogWriterMode.Disabled
                            }
                        },
                        logMessageBuilder = object : LogMessageBuilder {
                            override fun build(
                                tag: String,
                                message: String,
                                isSecure: Boolean,
                                allowLongMessages: Boolean,
                                logPriority: Int,
                            ): String {
                                return message
                            }
                        },
                        sensitiveLogMode = LoggerFactory.SensitiveLogMode.DependsOnBuildType,
                        logNonfatalsInDbugMode = LoggerFactory.LogNonfatalsInDebigMode.ENABLED,
                    )
                )
            }

            override fun lazy(tag: String): Lazy<LocalLogger> {
                return lazy { get(tag) }
            }
        }
    }

    private val recentAppsDispatcher: RecentAppsDispatcher by lazy {
        RecentAppsFactory(
            context = context,
            coroutineDispatchers = coroutineDispatchers,
            binderHelperFactory2 = BinderHelperFactory2Impl(),
            loggerFactory = loggerFactory
        ).createPersistentDispatcher()
    }

    private val recentAppsRepository: RecentAppsRepository by lazy {
        RecentAppsRepositoryImpl(recentAppsDispatcher = recentAppsDispatcher)
    }

    private val tray: Tray by lazy {
        TrayImpl(
            recentAppsRepository = recentAppsRepository,
            config = config,
            coroutineDispatchers = coroutineDispatchers
        )
    }

    private val voipModelInstance: VoIPModel by lazy { VoIPModel(analyticsSdk, tray) }

    private val dreamingEventsReceiver: DreamingEventsReceiver by lazy { DreamingEventsReceiver(context) }

    private val contactsRepository: ContactsRepository by lazy { ContactsRepository() }

    private val localConfig by lazy { LocalConfig(context) }

    private val stateRepository: AppStateRepository by lazy { AppStateRepository(context) }

    private val avatarLoader: AvatarLoader by lazy { AvatarLoaderImpl(context, config) }

    private val authRepositoryInternal: AuthRepository by lazy {
        val connectionsManager = ConnectionsManager.getInstance(UserConfig.selectedAccount)
        val sendFunction = SendFunction { tlObject, delegate, flags, connectionType, datacenterId ->
            connectionsManager.sendRequest(tlObject, delegate, flags, connectionType, datacenterId)
        }
        val cancelFunction = CancelFunction { requestToken -> connectionsManager.cancelRequest(requestToken, true) }

        val callbackManager = object : AuthRemoteApiImpl.CallbackManager {
            private val messagesController = MessagesController.getInstance(UserConfig.selectedAccount)

            override fun addLoginTokenUpdateCallback(callback: MessagesController.LoginTokenUpdateCallback) {
                messagesController.addLoginTokenUpdateCallback(callback)
            }

            override fun removeLoginTokenUpdateCallback(callback: MessagesController.LoginTokenUpdateCallback) {
                messagesController.removeLoginTokenUpdateCallback(callback)
            }
        }

        AuthRepositoryImpl(
            authApi = AuthRemoteApiImpl(
                sender = RequestSenderImpl(
                    sendFunction = sendFunction,
                    cancelFunction = cancelFunction
                ),
                callbackManager = callbackManager
            ),
            ioDispatcher = Dispatchers.IO,
            qrCodeFactory = QrCodeFactoryImpl(context),
            networkRepository = NetworkRepositoryImpl(
                notificationCenter = NotificationCenter.getInstance(0),
                connectionsManager = connectionsManager
            ),
        )
    }

    private val settings: Settings by lazy { SettingsFactory.create(context) }

    private val coroutineDispatchers: CoroutineDispatchers by lazy { CoroutineDispatchersImpl() }

    private val applicationScope = CoroutineScope(
        SupervisorJob() + coroutineDispatchers.default + CoroutineExceptionHandler { coroutineContext, throwable ->
            logger.warn(throwable) { "coroutine $coroutineContext exception" }
        }
    )

    private val callRepository: CallRepository by lazy {
        CallRepository()
    }

    private val callsFeature: CallsFeature by lazy {
        CallsFeatureImpl(
            callsNotificationsRepository = CallsNotificationsRepositoryImpl(),
            callsRepository = CallsRepositoryImpl(),
            callsDestination = StarCallLogDestinationProvider.getDestination(context, loggerFactory),
            coroutineDispatchers = coroutineDispatchers
        )
    }

    private val contactsFeature: ContactsFeature by lazy {
        ContactsFeatureImpl(
            contactsDestination = StarContactsDestinationProvider.getDestination(context, loggerFactory),
            contactsNotificationsRepository = ContactsNotificationsRepositoryImpl(),
            contactsRepository = ContactsRepositoryImpl(),
            coroutineDispatchers = coroutineDispatchers,
            config = config,
            avatarLoader = avatarLoader
        )
    }

    private val authorizationFeature: AuthorizationFeature by lazy {
        AuthorizationFeatureImpl(
            authorizationNotificationsRepository = AuthorizationNotificationsRepositoryImpl(),
            authorizationRepository = AuthorizationRepositoryImpl(avatarLoader),
            coroutineScope = applicationScope,
            contactsFeature = contactsFeature,
            callsFeature = callsFeature,
            coroutineDispatchers = coroutineDispatchers
        )
    }

    private val firmwareVersionRepository: FirmwareVersionRepository by lazy {
        FirmwareVersionRepositoryImpl(deviceInfoRepository = DeviceInfoRepositoryFactory(
            context = context,
            coroutineDispatchers = coroutineDispatchers,
            binderHelperFactory2 = BinderHelperFactory2Impl(),
            loggerFactory = loggerFactory,
            shareInScope = applicationScope
            ).create())
    }

    private val starNotificationManager: StarNotificationManager by lazy {
        StarNotificationManagerFactory(context = context, coroutineDispatchers = coroutineDispatchers).create()
    }

    private val starNotifications: StarNotifications by lazy {
        StarNotifications(starNotificationManager = starNotificationManager, mainDispatcher = Dispatchers.Main)
    }

    private val hdmiRouteEnforcer: HdmiRouteEnforcer by lazy {
        HdmiRouteEnforcerImpl(context = context, config = config, coroutineDispatchers = coroutineDispatchers)
    }

    private val micCameraStateRepository: MicCameraStateRepository by lazy {
        MicCameraStateRepositoryFactory(context, ru.sberdevices.common.coroutines.CoroutineDispatchers).create()
    }

    private val micStateHelper: MicStateHelper by lazy {
        MicStateHelper(micCameraStateRepository, applicationScope, coroutineDispatchers, loggerFactory)
    }

    private val cameraAvailabilityHelper: CameraAvailabilityHelper by lazy {
        CameraAvailabilityHelper(micCameraStateRepository, applicationScope, coroutineDispatchers, loggerFactory)
    }

    @JvmStatic
    fun getHdmiRouteEnforcerSharedInstance(): HdmiRouteEnforcer {
        return hdmiRouteEnforcer
    }

    @JvmStatic
    fun getCoroutineDispatchersSharedInstance(): CoroutineDispatchers = coroutineDispatchers

    @JvmStatic
    fun getAnalyticsSdkSharedInstance(): AnalyticsCollector = analyticsSdk

    @JvmStatic
    fun getVoIPModelSharedInstance(): VoIPModel = voipModelInstance

    @JvmStatic
    val config: Config by lazy { Config(context, getCoroutineDispatchersSharedInstance()) }

    @JvmStatic
    fun getDreamingEventsReceiverSharedInstance(): DreamingEventsReceiver = dreamingEventsReceiver

    @JvmStatic
    fun getContactsRepositorySharedInstance(): ContactsRepository = contactsRepository

    @JvmStatic
    fun getLocalConfigSharedInstance(): LocalConfig = localConfig

    @JvmStatic
    fun getAppStateRepository(): AppStateRepository = stateRepository

    @JvmStatic
    fun getSettingsSharedInstance(): Settings = settings

    @JvmStatic
    fun getSpotterStateRepository(): SpotterStateRepository {
        return try {
            SpotterStateRepositoryFactory(context, coroutineDispatchers).create()
        } catch (t: Throwable) {
            logger.warn { "SpotterStateRepositoryStub created instead of a real one" }
            SpotterStateRepositoryStub()
        }
    }

    @JvmStatic
    fun getCallManager(): CallManager {
        return try {
            CallManagerFactory.create(context)
        } catch (t: Throwable) {
            logger.warn { "CallManagerStub created instead of a real one" }
            CallManagerStub()
        }
    }

    @JvmStatic
    fun getVoiceQualityEnhancer(): VoiceQualityEnhancer {
        return try {
            VoiceQualityEnhancerFactory.create(context)
        } catch (t: Throwable) {
            logger.warn { "VoiceQualityEnhancerStub created instead of a real one" }
            VoiceQualityEnhancerStub()
        }
    }

    @JvmStatic
    fun getAuthRepository(): AuthRepository = authRepositoryInternal

    @JvmStatic
    fun getCallRepositorySharedInstance(): CallRepository {
        return callRepository
    }

    @JvmStatic
    fun getAuthorizationFeatureSharedInstance(): AuthorizationFeature {
        return authorizationFeature
    }

    @JvmStatic
    fun getVendorServiceImpl(): VendorServiceImpl {
        return VendorServiceImpl(
            coroutineDispatchers = coroutineDispatchers,
            authorizationFeature = authorizationFeature,
            vendorVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            contactsFeature = contactsFeature,
            callsFeature = callsFeature
        )
    }

    @JvmStatic
    fun getFirmwareVersionRepositorySharedInstance(): FirmwareVersionRepository {
        return firmwareVersionRepository
    }

    @JvmStatic
    fun getTraySharedInstance(): Tray {
        return tray
    }

    @JvmStatic
    fun getAvatarLoaderSharedInstance(): AvatarLoader {
        return avatarLoader
    }

    @JvmStatic
    fun getContactsFeatureSharedInstance(): ContactsFeature {
        return contactsFeature
    }

    @JvmStatic
    fun getStarNotificationsInstance(): StarNotifications = starNotifications

    @JvmStatic
    fun getMicStateHelperInstance(): MicStateHelper = micStateHelper

    @JvmStatic
    fun getCameraAvailabilityHelperInstance(): CameraAvailabilityHelper = cameraAvailabilityHelper
}
