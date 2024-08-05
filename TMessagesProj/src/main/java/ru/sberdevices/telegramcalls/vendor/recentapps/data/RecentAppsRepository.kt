package ru.sberdevices.telegramcalls.vendor.recentapps.data

import ru.sberdevices.services.recent.apps.RecentAppsDispatcher
import ru.sberdevices.services.recent.apps.entities.RecentApp

private const val APP_ID = "ru.sberdevices.telegramcalls"
private const val LABEL_WITH_INTEGRATED_SINGLE_CALLS_PLACE= "Вернуться в звонок"
private const val LAUNCH_CLASSNAME = "org.telegram.ui.LaunchActivity"
private const val ICON_URI_WITH_INTEGRATED_SINGLE_CALLS_PLACE =
    "android.resource://$APP_ID/mipmap/ic_launcher_integrated_with_single_calls_place"


/**
 * @author Ирина Карпенко on 01.12.2021
 */
interface RecentAppsRepository {
    suspend fun joinRecentApps()
    suspend fun leaveRecentApps()
}

class RecentAppsRepositoryImpl(
    private val recentAppsDispatcher: RecentAppsDispatcher
) : RecentAppsRepository {

    override suspend fun joinRecentApps() {
        recentAppsDispatcher.addAppToRecents(
            recentApp = RecentApp(
                id = APP_ID,
                packageName = APP_ID,
                label = LABEL_WITH_INTEGRATED_SINGLE_CALLS_PLACE,
                className = LAUNCH_CLASSNAME,
                deepLink = null,
                timeStamp = System.currentTimeMillis(),
                iconUri = ICON_URI_WITH_INTEGRATED_SINGLE_CALLS_PLACE
            )
        )
    }

    override suspend fun leaveRecentApps() {
        recentAppsDispatcher.removeAppFromRecents(APP_ID)
    }
}