package io.github.meko123456.syncbeats.core.domain.di

import org.koin.core.qualifier.named

/**
 * Koin qualifiers that more than one layer needs to name.
 *
 * [APP_SCOPE] is an application-lifetime CoroutineScope, used for writes that must survive a
 * screen being torn down — leaving a room, for instance, has to finish even though the
 * ViewModel that started it is already gone.
 *
 * It lives in the domain because the feature modules reference it and the data module provides
 * it. Declared in :core:data (where it started) a feature would have had to depend on the data
 * layer to name it, which is exactly the edge this refactor removes.
 */
val APP_SCOPE = named("appScope")
