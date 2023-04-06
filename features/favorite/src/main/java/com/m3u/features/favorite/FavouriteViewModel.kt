package com.m3u.features.favorite

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.m3u.core.architecture.BaseViewModel
import com.m3u.core.architecture.configuration.Configuration
import com.m3u.core.util.collection.filterNotNullKeys
import com.m3u.data.repository.FeedRepository
import com.m3u.data.repository.LiveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

@HiltViewModel
class FavouriteViewModel @Inject constructor(
    liveRepository: LiveRepository,
    feedRepository: FeedRepository,
    application: Application,
    private val configuration: Configuration,
) : BaseViewModel<FavoriteState, FavoriteEvent>(
    application = application,
    emptyState = FavoriteState()
) {
    init {
        writable.update {
            it.copy(
                rowCount = configuration.rowCount,
                godMode = configuration.godMode
            )
        }
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
        when (event) {
            is FavoriteEvent.SetRowCount -> setRowCount(event.target)
        }
    }

    private fun setRowCount(target: Int) {
        configuration.rowCount = target
        writable.update {
            it.copy(
                rowCount = target
            )
        }
    }
}
