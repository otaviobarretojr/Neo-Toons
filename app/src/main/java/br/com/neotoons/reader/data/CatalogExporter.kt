package br.com.neotoons.reader.data

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Instant

/** A metadata-only snapshot created through the same public API the catalog already uses. */
class CatalogExporter(private val api: NeoToonsApi) {

    data class Result(val file: File, val works: Int)

    private val gson = Gson()

    // Explicit field allowlist excludes uploaders and account/session data.
    private val workFields = listOf(
        "id", "slug", "title", "alternativeTitles", "synopsis",
        "coverUrl", "bannerUrl", "author", "artist", "status", "demographic",
        "releaseYear", "viewsCount", "ratingAverage", "ratingsCount",
        "latestChapterAt", "createdAt", "updatedAt"
    )
    private val chapterFields = listOf("id", "chapterNumber", "createdAt", "releasedAt")
    private val genreFields = listOf("id", "name", "slug", "description")

    /**
     * Makes one request per catalog page, spaced by 1.25s, in the foreground.
     * Aborts on HTTP 403/429, invalid JSON or a changing catalogue count.
     * Does not fetch image URLs or full chapter contents.
     */
    suspend fun export(cacheDirectory: File, progress: (Int, Int, Int) -> Unit): Result {
        val temp = File(cacheDirectory, "neotoons-catalogo-export.part")
        val ready = File(cacheDirectory, "neotoons-catalogo-export.json")
        withContext(Dispatchers.IO) { temp.delete() }
        var writer: java.io.BufferedWriter? = null
        var finished = false
        try {
            val rawGenres = api.rawGenres()
            val safeGenres = JsonArray()
            rawGenres.forEach { element ->
                if (element.isJsonObject) {
                    val genre = allowFields(element.asJsonObject, genreFields)
                    if (element.asJsonObject.has("_count")) {
                        val count = element.asJsonObject.get("_count")
                        if (count.isJsonObject) {
                            genre.add("_count", allowFields(count.asJsonObject, listOf("mangas")))
                        }
                    }
                    safeGenres.add(genre)
                }
            }
            writer = withContext(Dispatchers.IO) {
                temp.bufferedWriter(Charsets.UTF_8).also {
                    it.write("{\"schema_version\":1,\"source\":\"NeoToons public API\",")
                    it.write("\"metadata_only\":true,\"generated_at_utc\":")
                    it.write(gson.toJson(Instant.now().toString()))
                    it.write(",\"genres\":")
                    it.write(gson.toJson(safeGenres))
                    it.write(",\"works\":[")
                }
            }
            val knownIds = HashSet<String>()
            var page = 1
            var reportedTotal = -1
            var reportedPages = -1
            var wroteAny = false

            while (true) {
                delay(1250L)
                val response = api.rawCatalogue(page = page)
                val total = response.get("total")?.asInt
                    ?: throw IOException("API sem total de obras.")
                val pages = response.get("totalPages")?.asInt
                    ?: throw IOException("API sem total de páginas.")
                val items = response.get("items")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?: throw IOException("API sem lista de obras.")
                if (pages !in 1..2500 || total < 0 || items.size() > 24) {
                    throw IOException("Paginação inesperada; coleta interrompida.")
                }
                if (page == 1) {
                    reportedTotal = total
                    reportedPages = pages
                } else if (reportedTotal != total || reportedPages != pages) {
                    throw IOException("O total do catálogo mudou durante a coleta. Tente novamente.")
                }
                val batch = ArrayList<String>(items.size())
                for (element in items) {
                    if (!element.isJsonObject) throw IOException("Registro de obra inválido.")
                    val item = element.asJsonObject
                    val id = item.get("id")?.asString
                        ?: throw IOException("Obra sem identificador.")
                    if (id.isBlank()) throw IOException("Identificador vazio.")
                    if (!knownIds.add(id)) continue
                    batch.add(gson.toJson(sanitizeWork(item)))
                }
                val addComma = wroteAny
                val activeWriter = writer ?: throw IOException("Arquivo temporário não disponível.")
                withContext(Dispatchers.IO) {
                    if (batch.isNotEmpty()) {
                        if (addComma) activeWriter.write(",")
                        activeWriter.write(batch.joinToString(","))
                        activeWriter.flush()
                    }
                }
                if (batch.isNotEmpty()) wroteAny = true
                progress(page, reportedPages, knownIds.size)
                if (page >= reportedPages) break
                page++
            }
            if (reportedTotal != knownIds.size) {
                throw IOException(
                    "Backup incompleto: ${knownIds.size} obras de ${reportedTotal} previstas."
                )
            }
            val finalWriter = writer ?: throw IOException("Arquivo temporário não disponível.")
            withContext(Dispatchers.IO) {
                finalWriter.write("],\"reported_total\":${reportedTotal},")
                finalWriter.write("\"unique_count\":${knownIds.size},\"complete\":true}")
                finalWriter.flush()
                finalWriter.close()
                writer = null
                if (ready.exists() && !ready.delete()) {
                    throw IOException("Não foi possível substituir o backup anterior.")
                }
                if (!temp.renameTo(ready)) {
                    throw IOException("Não foi possível concluir o arquivo de backup.")
                }
            }
            finished = true
            return Result(ready, knownIds.size)
        } finally {
            if (!finished) withContext(NonCancellable + Dispatchers.IO) {
                try { writer?.close() } catch (_: Exception) {}
                temp.delete()
            }
        }
    }

    private fun allowFields(input: JsonObject, fields: List<String>): JsonObject {
        val output = JsonObject()
        for (field in fields) {
            if (input.has(field)) output.add(field, input.get(field).deepCopy())
        }
        return output
    }

    private fun sanitizeWork(input: JsonObject): JsonObject {
        val output = allowFields(input, workFields)
        for ((field, allowed) in listOf(
            "genres" to genreFields,
            "chapters" to chapterFields
        )) {
            val sanitized = JsonArray()
            val array = input.get(field)
            if (array != null && array.isJsonArray) {
                array.asJsonArray.forEach { element ->
                    if (element.isJsonObject) {
                        sanitized.add(allowFields(element.asJsonObject, allowed))
                    }
                }
            }
            output.add(field, sanitized)
        }
        val count = input.get("_count")
        if (count != null && count.isJsonObject) {
            output.add("_count",
                allowFields(count.asJsonObject, listOf("chapters", "bookmarks")))
        }
        return output
    }
}
