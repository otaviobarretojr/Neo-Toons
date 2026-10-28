package br.com.neotoons.reader.ui

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.neotoons.reader.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReaderRepository(application)
    val favorites = repository.favorites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val progress = repository.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var items by androidx.compose.runtime.mutableStateOf<List<Manga>>(emptyList())
        private set
    var genres by androidx.compose.runtime.mutableStateOf<List<Genre>>(emptyList())
        private set
    var total by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var currentPage by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var totalPages by androidx.compose.runtime.mutableIntStateOf(1)
        private set
    var catalogLoading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var catalogError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var detail by androidx.compose.runtime.mutableStateOf<Manga?>(null)
        private set
    var detailLoading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var detailError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var chapter by androidx.compose.runtime.mutableStateOf<ReaderChapter?>(null)
        private set
    var chapterLoading by androidx.compose.runtime.mutableStateOf(false)
        private set
    var chapterError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    var backupRunning by androidx.compose.runtime.mutableStateOf(false)
        private set
    var backupReady by androidx.compose.runtime.mutableStateOf(false)
        private set
    var backupPage by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var backupPages by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var backupCount by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    var backupStatus by androidx.compose.runtime.mutableStateOf("")
        private set
    var backupError by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    private var backupJob: Job? = null
    private var backupFile: File? = null

    private var listJob: Job? = null
    private var detailJob: Job? = null
    private var readerJob: Job? = null
    private var filters = Filters()
    data class Filters(val search: String = "", val genre: String? = null,
        val sort: String = "newest", val status: String? = null)

    init {
        viewModelScope.launch {
            try { genres = repository.genres() }
            catch (_: Exception) { /* o catálogo permanece disponível */ }
        }
    }

    fun refresh(search: String, genre: String?, sort: String, status: String?) {
        filters = Filters(search.trim(), genre, sort, status)
        listJob?.cancel()
        items = emptyList()
        total = 0
        currentPage = 0
        totalPages = 1
        catalogError = null
        fetchPage(1)
    }
    fun nextPage() {
        if (!catalogLoading && currentPage < totalPages) fetchPage(currentPage + 1)
    }
    private fun fetchPage(page: Int) {
        listJob?.cancel()
        val request = filters
        listJob = viewModelScope.launch {
            catalogLoading = true
            catalogError = null
            try {
                val result = repository.list(page, request.search, request.genre, request.sort, request.status)
                if (request != filters) return@launch
                items = if (page == 1) result.items else (items + result.items).distinctBy { it.id }
                currentPage = result.page
                totalPages = result.totalPages
                total = result.total
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                catalogError = friendlyError(e)
            } finally { catalogLoading = false }
        }
    }
    fun loadDetails(slug: String) {
        detailJob?.cancel()
        detail = null
        detailError = null
        detailJob = viewModelScope.launch {
            detailLoading = true
            try { detail = repository.details(slug) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                detailError = friendlyError(e)
            } finally { detailLoading = false }
        }
    }
    fun loadChapter(slug: String, number: String) {
        readerJob?.cancel()
        chapter = null
        chapterError = null
        readerJob = viewModelScope.launch {
            chapterLoading = true
            try { chapter = repository.read(slug, number) }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                chapterError = friendlyError(e)
            } finally { chapterLoading = false }
        }
    }
    fun setFavorite(manga: Manga, isFavorite: Boolean) {
        viewModelScope.launch {
            if (isFavorite) repository.remove(manga.id)
            else repository.add(Favorite(manga.id, manga.slug, manga.title,
                manga.coverUrl, manga.latestChapterAt))
        }
    }
    fun saveProgress(mangaId: String, slug: String, title: String, coverUrl: String?,
        number: String, pageIndex: Int) {
        if (mangaId.isBlank()) return
        viewModelScope.launch {
            repository.save(ReadingProgress(mangaId, slug, title, coverUrl, number, pageIndex))
        }
    }

    fun startCatalogBackup() {
        if (backupRunning) return
        backupJob?.cancel()
        backupReady = false
        backupError = null
        backupPage = 0
        backupPages = 0
        backupCount = 0
        backupStatus = "Consultando o catálogo público…"
        backupRunning = true
        backupJob = viewModelScope.launch {
            try {
                val result = CatalogExporter(ApiProvider.service).export(
                    getApplication<Application>().cacheDir
                ) { page, totalPages, uniqueCount ->
                    backupPage = page
                    backupPages = totalPages
                    backupCount = uniqueCount
                    backupStatus = "Verificando os registros…"
                }
                backupFile = result.file
                backupReady = true
                backupStatus = "Backup validado e pronto para salvar."
            } catch (e: CancellationException) {
                backupStatus = "Coleta cancelada."
                throw e
            } catch (e: Exception) {
                backupError = friendlyError(e)
                backupStatus = "O backup não foi concluído; nenhum arquivo incompleto será salvo."
            } finally {
                backupRunning = false
            }
        }
    }

    fun cancelCatalogBackup() {
        backupJob?.cancel()
        backupRunning = false
        backupReady = false
    }

    fun saveCatalogBackup(uri: Uri, resolver: ContentResolver) {
        val file = backupFile
        if (!backupReady || file == null || !file.exists()) {
            backupError = "O arquivo temporário não está disponível. Gere um novo backup."
            return
        }
        backupStatus = "Salvando no destino escolhido…"
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = resolver.openOutputStream(uri, "w")
                        ?: throw java.io.IOException("Não foi possível abrir o destino.")
                    output.use { stream -> file.inputStream().use { it.copyTo(stream) } }
                }
                backupStatus = "Arquivo salvo no destino escolhido."
            } catch (e: Exception) {
                backupError = "Falha ao salvar: ${e.message ?: e.javaClass.simpleName}"
                backupStatus = "Você pode tentar salvar novamente."
            }
        }
    }

    private fun friendlyError(e: Exception) = when(e) {
        is HttpException -> when(e.code()) {
            401, 403 -> "A API restringiu o acesso. Não tentaremos contornar o bloqueio."
            404 -> "Este conteúdo não foi encontrado."
            429 -> "Limite de consultas atingido. Tente novamente mais tarde."
            else -> "Erro HTTP ${e.code()} ao consultar a NeoToons."
        }
        is IOException -> "Falha na conexão. Verifique sua internet."
        else -> "Não foi possível processar os dados recebidos."
    }
}
