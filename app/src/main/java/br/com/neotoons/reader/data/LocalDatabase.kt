package br.com.neotoons.reader.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class Favorite(
    @PrimaryKey val mangaId: String,
    val slug: String, val title: String, val coverUrl: String?,
    val latestChapterAt: String?,
    val addedAt: Long = System.currentTimeMillis()
)
@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val mangaId: String,
    val slug: String, val title: String, val coverUrl: String?,
    val chapterNumber: String, val pageIndex: Int,
    val updatedAt: Long = System.currentTimeMillis()
)
@Dao
interface ReaderDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun observeFavorites(): Flow<List<Favorite>>
    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeProgress(): Flow<List<ReadingProgress>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFavorite(favorite: Favorite)
    @Query("DELETE FROM favorites WHERE mangaId = :id")
    suspend fun deleteFavorite(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgress)
}
@Database(entities = [Favorite::class, ReadingProgress::class], version = 1, exportSchema = false)
abstract class ReaderDatabase : RoomDatabase() {
    abstract fun readerDao(): ReaderDao
}
