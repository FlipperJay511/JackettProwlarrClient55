package com.aggregatorx.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aggregatorx.app.data.model.*
import com.aggregatorx.app.data.repository.AggregatorRepository
import com.aggregatorx.app.engine.media.*
import com.aggregatorx.app.engine.token.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VideoPreviewResult(val videoUrl: String, val headers: Map<String, String> = emptyMap())

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: AggregatorRepository,
    private val videoExtractor: VideoExtractorEngine,
    private val advancedExtractor: AdvancedVideoExtractorEngine,
    private val downloadManager: DownloadManager,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _providerResults = MutableStateFlow<List<ProviderSearchResults>>(emptyList())
    val providerResults: StateFlow<List<ProviderSearchResults>> = _providerResults.asStateFlow()

    private val _videoExtractionState = MutableStateFlow<VideoExtractionState>(VideoExtractionState.Idle)
    val videoExtractionState: StateFlow<VideoExtractionState> = _videoExtractionState.asStateFlow()

    private val _likedUrls = MutableStateFlow<Set<String>>(emptySet())
    val likedUrls: StateFlow<Set<String>> = _likedUrls.asStateFlow()

    private val _isDiscoveryPaused = MutableStateFlow(false)
    val isDiscoveryPaused: StateFlow<Boolean> = _isDiscoveryPaused.asStateFlow()

    private val _isLoop2Running = MutableStateFlow(false)
    val isLoop2Running: StateFlow<Boolean> = _isLoop2Running.asStateFlow()

    private val _tokenResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val tokenResults: StateFlow<List<SearchResult>> = _tokenResults.asStateFlow()

    private val _myAiResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val myAiResults: StateFlow<List<SearchResult>> = _myAiResults.asStateFlow()

    private var currentSearchJob: Job? = null

    init {
        viewModelScope.launch { repository.getRecentSearches().collect { _uiState.update { s -> s.copy(recentSearches = it) } } }
        viewModelScope.launch { _likedUrls.value = repository.getAllLikedUrls() }
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) return
        currentSearchJob?.cancel()
        currentSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchCompleted = false) }
            repository.searchAllProviders(query, true).collect { pr ->
                _providerResults.update { list -> (list.filter { it.provider.id != pr.provider.id } + pr) }
            }
            _uiState.update { it.copy(isSearching = false, searchCompleted = true) }
        }
    }

    fun panicRefresh() {
        clearSearchCache()
        _providerResults.value = emptyList()
        search()
    }

    fun toggleDiscoveryPause() { _isDiscoveryPaused.value = !_isDiscoveryPaused.value }
    fun clearError() { _uiState.update { it.copy(error = null) } }
    fun resetVideoState() { _videoExtractionState.value = VideoExtractionState.Idle }
    fun updateQuery(q: String) { _uiState.update { it.copy(query = q) } }
    fun clearSearchCache() { repository.clearSearchCache() }
    
    fun extractVideoUrl(result: SearchResult) {
        _videoExtractionState.value = VideoExtractionState.Extracting(result.url)
        viewModelScope.launch {
            val url = videoExtractor.extractVideoUrl(result.url) ?: advancedExtractor.extractVideoUrl(result.url)
            _videoExtractionState.value = if (url != null) VideoExtractionState.Success(url, result.title) 
                                         else VideoExtractionState.Error("Extraction failed")
        }
    }

    fun downloadVideoUrl(url: String, title: String) {
        viewModelScope.launch { downloadManager.downloadDirect(url, title) }
    }

    fun toggleLike(result: SearchResult) {
        viewModelScope.launch { repository.toggleLike(result); _likedUrls.value = repository.getAllLikedUrls() }
    }
}

sealed class VideoExtractionState {
    data object Idle : VideoExtractionState()
    data class Extracting(val url: String) : VideoExtractionState()
    data class Success(val videoUrl: String, val title: String, val headers: Map<String, String> = emptyMap()) : VideoExtractionState()
    data class Error(val message: String) : VideoExtractionState()
}

data class SearchUiState(val query: String = "", val isSearching: Boolean = false, val searchCompleted: Boolean = false, val error: String? = null, val recentSearches: List<com.aggregatorx.app.data.model.SearchHistoryEntry> = emptyList())
