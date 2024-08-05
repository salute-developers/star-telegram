package ru.sberdevices.sbdv.config

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOn
import com.sdkit.base.core.threading.coroutines.CoroutineDispatchers
import ru.sberdevices.services.appconfig.ApplicationConfigRepositoryFactory

interface AppConfigProvider {
    val configFlow: Flow<String>
}

class AppConfigProviderImpl(context: Context, coroutineDispatchers: CoroutineDispatchers) : AppConfigProvider {

    private val provider = ApplicationConfigRepositoryFactory(context, coroutineDispatchers).createRaw()

    override val configFlow = provider.rawConfigFlow.filterNotNull().flowOn(coroutineDispatchers.io)
}

class AppConfigProviderStub : AppConfigProvider {
    override val configFlow = MutableStateFlow<String?>(null).filterNotNull()
}
