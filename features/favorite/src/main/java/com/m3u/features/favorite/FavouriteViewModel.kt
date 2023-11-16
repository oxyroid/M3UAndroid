package com.m3u.features.favorite

import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.architecture.viewmodel.BaseViewModel
import com.m3u.core.util.collections.filterNotNullKeys
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    liveRepository: LiveRepository,
    feedRepository: FeedRepository,
    configuration: Configuration,
) : BaseViewModel<FavoriteState, FavoriteEvent, Unit>(
    emptyState = FavoriteState(
        configuration = configuration
    )
) {
    init {
        liveRepository
            .observeAll()
            .map { lives ->
                lives.filter { it.favourite }
            }
            .onEach { lives ->
                writable.update { state ->
                    state.copy(
                        details = lives
                            .groupBy { it.feedUrl }
                            .mapKeys { feedRepository.get(it.key)?.title }
                            .filterNotNullKeys()
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onEvent(event: FavoriteEvent) {

    }
}
