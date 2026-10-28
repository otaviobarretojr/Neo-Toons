package br.com.neotoons.reader.data

import com.google.gson.annotations.SerializedName

data class MangaPage(
    val items: List<Manga> = emptyList(), val total: Int = 0,
    val page: Int = 1, val limit: Int = 24, val totalPages: Int = 1
)
data class Manga(
    val id: String = "", val title: String = "", val slug: String = "",
    val synopsis: String? = null, val coverUrl: String? = null,
    val bannerUrl: String? = null, val author: String? = null,
    val artist: String? = null, val status: String? = null,
    val demographic: String? = null, val ratingAverage: String? = null,
    val latestChapterAt: String? = null, val updatedAt: String? = null,
    val genres: List<Genre>? = null, val chapters: List<Chapter>? = null,
    @SerializedName("_count") val count: MangaCount? = null
)
data class MangaCount(val chapters: Int = 0, val bookmarks: Int = 0)
data class Genre(val id: String = "", val name: String = "", val slug: String = "")
data class Chapter(
    val id: String = "", val chapterNumber: String = "",
    val title: String? = null, val releasedAt: String? = null
)
data class MangaImage(val id: String = "", val pageNumber: Int = 0, val imageUrl: String = "")
data class ReaderChapter(
    val id: String = "", val mangaId: String = "",
    val chapterNumber: String = "", val title: String? = null,
    val pages: List<MangaImage> = emptyList(),
    val prevChapter: Chapter? = null, val nextChapter: Chapter? = null
)
