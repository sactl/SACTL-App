package me.capcom.smsgateway.modules.heartbeat

import me.capcom.smsgateway.modules.settings.PreferencesStorage
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val heartbeatModule = module {
    factory {
        HeartbeatSettings(
            PreferencesStorage(get(), "heartbeat")
        )
    }
    singleOf(::HeartbeatService)
}
