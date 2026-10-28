package br.com.neotoons.reader.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NeoToonsApi {
    @GET("mangas")
    suspend fun catalog(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("sortBy") sortBy: String = "newest",
        @Query("search") search: String? = null,
        @Query("genreSlug") genreSlug: String? = null,
        @Query("demographic") demographic: String? = null,
        @Query("status") status: String? = null
    ): MangaPage

    @GET("genres")
    suspend fun genres(): List<Genre>

    @GET("genres")
    suspend fun rawGenres(): com.google.gson.JsonArray

    @GET("mangas")
    suspend fun rawCatalogue(
        @Query("page") page: Int,
        @Query("limit") limit: Int = 24,
        @Query("sortBy") sortBy: String = "newest"
    ): com.google.gson.JsonObject

    @GET("mangas/{slug}")
    suspend fun manga(@Path("slug") slug: String): Manga

    @GET("chapters/read/{slug}/{chapterNumber}")
    suspend fun chapter(
        @Path("slug") slug: String,
        @Path("chapterNumber") chapterNumber: String
    ): ReaderChapter
}

object ApiProvider {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    val service: NeoToonsApi = Retrofit.Builder()
        .baseUrl("https://api.neotoons.com.br/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NeoToonsApi::class.java)
}
