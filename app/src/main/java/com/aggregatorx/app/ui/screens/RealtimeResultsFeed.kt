package com.aggregatorx.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aggregatorx.app.data.model.ProviderSearchResults
import com.aggregatorx.app.data.model.SearchResult
import kotlinx.coroutines.flow.Flow

/**
 * REAL-TIME RESULTS FEED
 * Shows results as each provider finishes searching
 * Updates UI instantly without waiting for all providers to complete
 */
@Composable
fun RealtimeResultsFeed(
    providerResultsFlow: Flow<ProviderSearchResults>,
    likedUrls: Set<String>,
    onWatch: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onBrowser: (SearchResult) -> Unit,
    onLike: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val allResults = remember { mutableStateListOf<ProviderSearchResults>() }
    val listState = rememberLazyListState()

    // Collect results as they stream in from providers
    LaunchedEffect(providerResultsFlow) {
        providerResultsFlow.collect { providerResult ->
            // Update or add provider results
            val existingIndex = allResults.indexOfFirst { it.provider.id == providerResult.provider.id }
            if (existingIndex >= 0) {
                allResults[existingIndex] = providerResult
            } else {
                allResults.add(providerResult)
            }
            
            // Auto-scroll to new results
            if (allResults.isNotEmpty()) {
                listState.animateScrollToItem(0)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(12.dp)
    ) {
        items(allResults, key = { it.provider.id }) { providerResult ->
            ProviderResultsSection(
                providerResult = providerResult,
                likedUrls = likedUrls,
                onWatch = onWatch,
                onDownload = onDownload,
                onBrowser = onBrowser,
                onLike = onLike
            )
        }
    }
}

/**
 * Individual provider section with streaming animation
 */
@Composable
fun ProviderResultsSection(
    providerResult: ProviderSearchResults,
    likedUrls: Set<String>,
    onWatch: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onBrowser: (SearchResult) -> Unit,
    onLike: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { 50 }) + fadeIn(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Provider Header with Status
                ProviderHeader(
                    providerName = providerResult.provider.name,
                    resultCount = providerResult.results.size,
                    success = providerResult.success,
                    isLoading = false,
                    usedWebView = providerResult.usedWebView,
                    errorMessage = providerResult.errorMessage
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Results Grid - Stream in as they arrive
                if (providerResult.results.isNotEmpty()) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(providerResult.results, key = { it.url }) { result ->
                            ResultItemCard(
                                result = result,
                                isLiked = result.url in likedUrls,
                                onWatch = onWatch,
                                onDownload = onDownload,
                                onBrowser = onBrowser,
                                onLike = onLike
                            )
                        }
                    }
                } else if (!providerResult.success) {
                    ErrorMessageCard(providerResult.errorMessage ?: "No results found")
                } else {
                    LoadingIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

/**
 * Provider header with live status badge
 */
@Composable
fun ProviderHeader(
    providerName: String,
    resultCount: Int,
    success: Boolean,
    isLoading: Boolean,
    usedWebView: Boolean,
    errorMessage: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = providerName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status Badge
                AnimatedVisibility(
                    visible = isLoading,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 1.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Searching...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                AnimatedVisibility(
                    visible = success && !isLoading,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$resultCount results",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (usedWebView) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Used WebView",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }
    }
}

/**
 * Individual result card with minimal animation
 */
@Composable
fun ResultItemCard(
    result: SearchResult,
    isLiked: Boolean,
    onWatch: (SearchResult) -> Unit,
    onDownload: (SearchResult) -> Unit,
    onBrowser: (SearchResult) -> Unit,
    onLike: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { 100 }) + fadeIn(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(2.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Title
                Text(
                    text = result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2
                )

                if (!result.description.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onBrowser(result) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInBrowser,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open", fontSize = 10.sp)
                    }

                    FilledTonalButton(
                        onClick = { onDownload(result) },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", fontSize = 10.sp)
                    }

                    IconButton(
                        onClick = { onLike(result) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Like",
                            modifier = Modifier.size(16.dp),
                            tint = if (isLiked) MaterialTheme.colorScheme.error 
                                   else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Error state card
 */
@Composable
fun ErrorMessageCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

/**
 * Loading indicator
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Searching...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
