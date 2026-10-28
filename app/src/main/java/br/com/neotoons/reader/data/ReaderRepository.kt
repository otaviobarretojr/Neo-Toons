package br.com.neotoons.reader.data

import android.content.Context
import androidx.room.Room

class ReaderRepository(context: Context) {
    private val api = ApiProvider.service
    private val dao = Room.databaseBuilder(context.applicationContext, ReaderDatabase::class.java, "reader.db")
        .build().readerDao()
    val favorites = dao.observeFavorites()
    val progress = dao.observeProgress()

    suspend fun list(page: Int, query: String, genre: String?, order: String, status: String?) =
        api.catalog(page = page, search = query.takeIf { it.isNotBlank() },
            genreSlug = genre, sortBy = order, status = status)
    suspend fun genres() = api.genres()
    suspend fun details(slug: String) = api.manga(slug)
    suspend fun read(slug: String, number: String) = api.chapter(slug, number)
    suspend fun add(favorite: Favorite) = dao.saveFavorite(favorite)
    suspend fun remove(id: String) = dao.deleteFavorite(id)
    suspend fun save(progress: ReadingProgress) = dao.saveProgress(progress)
}
