package com.aggregatorx.app.data.database

import androidx.room.*
import com.aggregatorx.app.data.model.LearnedUserProfile
import com.aggregatorx.app.data.model.LikedResult
import com.aggregatorx.app.data.model.Provider
import com.aggregatorx.app.data.model.ScrapingConfig
import com.aggregatorx.app.data.model.SearchHistoryEntry
import com.aggregatorx.app.data.model.SiteAnalysis
import com.aggregatorx.app.data.model.UserPreferences
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 50")
    fun getRecentSearches(): Flow<List<SearchHistoryEntry>>
    
    @Query("SELECT * FROM search_history WHERE query LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchHistory(query: String): List<SearchHistoryEntry>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearch(entry: SearchHistoryEntry)
    
    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun deleteSearch(id: String)
    
    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
    
    @Query("SELECT query FROM search_history GROUP BY query ORDER BY COUNT(*) DESC, MAX(timestamp) DESC LIMIT 10")
    suspend fun getMostSearchedQueries(): List<String>
}

/**
 * User Preferences DAO - Tracks user behavior for intelligent suggestions
 */
@Dao
interface UserPreferencesDao {
    @Query("SELECT * FROM user_preferences WHERE id = 1")
    suspend fun getPreferences(): UserPreferences?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferences(preferences: UserPreferences)
    
    @Query("UPDATE user_preferences SET clickedCategories = :categories WHERE id = 1")
    suspend fun updateClickedCategories(categories: String)
    
    @Query("UPDATE user_preferences SET watchedGenres = :genres WHERE id = 1")
    suspend fun updateWatchedGenres(genres: String)
    
    @Query("UPDATE user_preferences SET preferredQualities = :qualities WHERE id = 1")
    suspend fun updatePreferredQualities(qualities: String)
}

/**
 * Liked Results DAO - Tracks which results the user has liked.
 * Powers the preference learning system.
 */
@Dao
interface LikedResultDao {
    @Query("SELECT * FROM liked_results ORDER BY likedAt DESC")
    fun getAllLikedResults(): Flow<List<LikedResult>>

    @Query("SELECT * FROM liked_results ORDER BY likedAt DESC")
    suspend fun getAllLikedResultsSync(): List<LikedResult>

    @Query("SELECT * FROM liked_results WHERE url = :url LIMIT 1")
    suspend fun getLikedByUrl(url: String): LikedResult?

    @Query("SELECT COUNT(*) FROM liked_results")
    suspend fun getLikedCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(liked: LikedResult)

    @Query("DELETE FROM liked_results WHERE url = :url")
    suspend fun removeLikeByUrl(url: String)

    @Query("DELETE FROM liked_results WHERE id = :id")
    suspend fun removeLikeById(id: String)

    @Query("SELECT url FROM liked_results")
    suspend fun getAllLikedUrls(): List<String>

    /** Most-liked providers (by count) for weighting */
    @Query("SELECT providerName, COUNT(*) as cnt FROM liked_results GROUP BY providerName ORDER BY cnt DESC LIMIT 20")
    suspend fun getTopLikedProviders(): List<ProviderLikeCount>

    /** Most common keywords across all liked result titles */
    @Query("SELECT titleKeywords FROM liked_results ORDER BY likedAt DESC LIMIT 200")
    suspend fun getRecentLikedKeywords(): List<String>
}

/** Helper data class for provider like counts */
data class ProviderLikeCount(
    val providerName: String,
    val cnt: Int
)

/**
 * Learned User Profile DAO - Stores the aggregated preference model.
 */
@Dao
interface LearnedProfileDao {
    @Query("SELECT * FROM learned_profile WHERE id = 1")
    suspend fun getProfile(): LearnedUserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProfile(profile: LearnedUserProfile)
}
