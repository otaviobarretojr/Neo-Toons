package br.com.neotoons.reader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.neotoons.reader.data.*
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private sealed interface Screen {
    data object Catalog : Screen
    data object Library : Screen
    data class Details(val slug: String) : Screen
    data class Reading(val id: String, val slug: String, val chapter: String,
                       val title: String, val coverUrl: String?) : Screen
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderApp(vm: ReaderViewModel = viewModel()) {
    var screen by remember { mutableStateOf<Screen>(Screen.Catalog) }
    var search by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var order by remember { mutableStateOf("newest") }
    var tab by remember { mutableIntStateOf(0) }
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()

    LaunchedEffect(search, genre, status, order) {
        delay(if (search.isNotBlank()) 300L else 50L)
        vm.refresh(search, genre, order, status)
    }
    fun details(slug: String) {
        vm.loadDetails(slug)
        screen = Screen.Details(slug)
    }
    fun read(id: String, slug: String, chapter: String, title: String, cover: String?) {
        vm.loadChapter(slug, chapter)
        screen = Screen.Reading(id, slug, chapter, title, cover)
    }
    fun back() {
        screen = when (val current = screen) {
            is Screen.Reading -> Screen.Details(current.slug)
            is Screen.Details -> if (tab == 0) Screen.Catalog else Screen.Library
            else -> Screen.Catalog
        }
    }
    BackHandler(screen is Screen.Details || screen is Screen.Reading) { back() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(when (val p = screen) {
                        Screen.Catalog -> "Neo-Toons"
                        Screen.Library -> "Minha biblioteca"
                        is Screen.Details -> "Detalhes"
                        is Screen.Reading -> p.title
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    if (screen is Screen.Details || screen is Screen.Reading) {
                        IconButton(onClick = { back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    if (screen is Screen.Catalog) {
                        IconButton(onClick = { vm.refresh(search, genre, order, status) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Recarregar")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (screen is Screen.Catalog || screen is Screen.Library) {
                NavigationBar {
                    NavigationBarItem(selected = screen is Screen.Catalog,
                        onClick = { tab = 0; screen = Screen.Catalog },
                        icon = { Icon(Icons.Default.Explore, null) }, label = { Text("Catálogo") })
                    NavigationBarItem(selected = screen is Screen.Library,
                        onClick = { tab = 1; screen = Screen.Library },
                        icon = { Icon(Icons.Default.Bookmarks, null) }, label = { Text("Biblioteca") })
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val p = screen) {
                Screen.Catalog -> CatalogView(
                    vm, search, { search = it }, genre, { genre = it },
                    status, { status = it }, order, { order = it }, ::details
                )
                Screen.Library -> LibraryView(favorites, progress, ::details,
                    { x -> read(x.mangaId, x.slug, x.chapterNumber, x.title, x.coverUrl) })
                is Screen.Details -> DetailView(
                    vm, p.slug, favorites.any { it.slug == p.slug },
                    progress.firstOrNull { it.slug == p.slug },
                    { manga, chapter -> read(manga.id, manga.slug, chapter, manga.title, manga.coverUrl) }
                )
                is Screen.Reading -> ReaderView(vm, p, progress.firstOrNull { it.mangaId == p.id }) {
                    number -> read(p.id, p.slug, number, p.title, p.coverUrl)
                }
            }
        }
    }
}

@Composable
private fun CatalogView(
    vm: ReaderViewModel, search: String, setSearch: (String) -> Unit,
    genre: String?, setGenre: (String?) -> Unit,
    status: String?, setStatus: (String?) -> Unit,
    order: String, setOrder: (String) -> Unit, onOpen: (String) -> Unit
) {
    var genrePicker by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var statusMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = search, onValueChange = setSearch, singleLine = true,
            placeholder = { Text("Pesquisar por título") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = genre != null, onClick = { genrePicker = true },
                label = { Text(vm.genres.firstOrNull { it.slug == genre }?.name ?: "Gêneros") })
            Box {
                FilterChip(selected = order != "newest", onClick = { sortMenu = true },
                    label = { Text(when(order) {
                        "views" -> "Mais vistos"; "rating" -> "Avaliação"; "az" -> "A–Z"
                        "bookmarks" -> "Mais favoritos"; else -> "Recentes"
                    }) })
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    listOf("newest" to "Recentes", "views" to "Mais vistos",
                        "rating" to "Avaliação", "az" to "A–Z",
                        "bookmarks" to "Mais favoritos").forEach { (value, title) ->
                        DropdownMenuItem(text = { Text(title) },
                            onClick = { setOrder(value); sortMenu = false })
                    }
                }
            }
            Box {
                FilterChip(selected = status != null, onClick = { statusMenu = true },
                    label = { Text(when(status) {
                        "ONGOING" -> "Em lançamento"; "COMPLETED" -> "Concluído"
                        "HIATUS" -> "Hiato"; else -> "Status"
                    }) })
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    listOf(null to "Todos", "ONGOING" to "Em lançamento",
                        "COMPLETED" to "Concluído", "HIATUS" to "Hiato").forEach { (value, name) ->
                        DropdownMenuItem(text = { Text(name) },
                            onClick = { setStatus(value); statusMenu = false })
                    }
                }
            }
        }
        Text("${vm.total} obras · página ${vm.currentPage}/${vm.totalPages}",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium)
        if (vm.items.isEmpty() && vm.catalogError != null) {
            ErrorView(vm.catalogError ?: "Falha na consulta") {
                vm.refresh(search, genre, order, status)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vm.items, key = { it.id }) { manga -> MangaCard(manga) { onOpen(manga.slug) } }
                item {
                    when {
                        vm.catalogLoading -> Box(Modifier.fillMaxWidth().padding(18.dp),
                            contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        vm.catalogError != null -> ErrorView(vm.catalogError ?: "Erro") { vm.nextPage() }
                        vm.currentPage < vm.totalPages -> OutlinedButton(onClick = { vm.nextPage() },
                            modifier = Modifier.fillMaxWidth()) { Text("Carregar mais") }
                        vm.items.isEmpty() -> Text("Nenhuma obra encontrada.")
                    }
                }
            }
        }
    }
    if (genrePicker) {
        AlertDialog(onDismissRequest = { genrePicker = false },
            title = { Text("Escolher gênero") },
            text = {
                LazyColumn(Modifier.heightIn(max = 430.dp)) {
                    item {
                        TextButton(onClick = { setGenre(null); genrePicker = false }) {
                            Text("Todos os gêneros")
                        }
                    }
                    items(vm.genres, key = { it.id }) { g ->
                        TextButton(onClick = { setGenre(g.slug); genrePicker = false },
                            modifier = Modifier.fillMaxWidth()) {
                            Text(g.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { genrePicker = false }) { Text("Fechar") }
            }
        )
    }
}

@Composable
private fun MangaCard(manga: Manga, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(model = manga.coverUrl, contentDescription = "Capa de ${manga.title}",
                modifier = Modifier.size(90.dp, 126.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(manga.title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium, maxLines = 3)
                Text(manga.author ?: "Autor não informado", maxLines = 1,
                    style = MaterialTheme.typography.bodySmall)
                Text(when(manga.status) {
                    "ONGOING" -> "Em lançamento"; "COMPLETED" -> "Concluído"
                    "HIATUS" -> "Hiato"; else -> "Status não informado"
                }, style = MaterialTheme.typography.labelMedium)
                Text("${manga.count?.chapters ?: manga.chapters?.size ?: 0} capítulos",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LibraryView(
    favorites: List<Favorite>, progress: List<ReadingProgress>,
    onOpen: (String) -> Unit, onContinue: (ReadingProgress) -> Unit
) {
    if (favorites.isEmpty() && progress.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Sua biblioteca está vazia. Abra uma obra para começar a ler.")
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (progress.isNotEmpty()) {
            item { Text("Continuar lendo", style = MaterialTheme.typography.titleLarge) }
            items(progress, key = { "progress_${it.mangaId}" }) { p ->
                Card(Modifier.fillMaxWidth().clickable { onContinue(p) }) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = p.coverUrl, contentDescription = null,
                            modifier = Modifier.size(65.dp, 90.dp), contentScale = ContentScale.Crop)
                        Column {
                            Text(p.title, style = MaterialTheme.typography.titleMedium)
                            Text("Capítulo ${p.chapterNumber} · página ${p.pageIndex + 1}")
                        }
                    }
                }
            }
        }
        if (favorites.isNotEmpty()) {
            item { Text("Favoritos", style = MaterialTheme.typography.titleLarge) }
            items(favorites, key = { "favorite_${it.mangaId}" }) { fav ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(fav.slug) }) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AsyncImage(model = fav.coverUrl, contentDescription = null,
                            modifier = Modifier.size(65.dp, 90.dp), contentScale = ContentScale.Crop)
                        Text(fav.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailView(
    vm: ReaderViewModel, slug: String, isFavorite: Boolean,
    progress: ReadingProgress?, onRead: (Manga, String) -> Unit
) {
    val manga = vm.detail
    if (vm.detailLoading && manga == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (manga == null || manga.slug != slug) {
        ErrorView(vm.detailError ?: "Não foi possível abrir a obra.") { vm.loadDetails(slug) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AsyncImage(model = manga.coverUrl, contentDescription = null,
                    modifier = Modifier.size(126.dp, 180.dp), contentScale = ContentScale.Crop)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(manga.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text(manga.author ?: "Autor não informado")
                    Text("${manga.count?.chapters ?: manga.chapters?.size ?: 0} capítulos")
                    FilledTonalButton(onClick = { vm.setFavorite(manga, isFavorite) }) {
                        Icon(if (isFavorite) Icons.Default.BookmarkRemove
                            else Icons.Default.BookmarkAdd, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (isFavorite) "Remover" else "Favoritar")
                    }
                }
            }
        }
        if (progress != null) {
            item {
                Button(onClick = { onRead(manga, progress.chapterNumber) },
                    modifier = Modifier.fillMaxWidth()) {
                    Text("Continuar no capítulo ${progress.chapterNumber}")
                }
            }
        }
        if (!manga.synopsis.isNullOrBlank()) {
            item { Text("Sinopse", style = MaterialTheme.typography.titleMedium) }
            item { Text(manga.synopsis) }
        }
        item { Text("Capítulos", style = MaterialTheme.typography.titleLarge) }
        items(manga.chapters.orEmpty(), key = { it.id }) { chapter ->
            OutlinedCard(Modifier.fillMaxWidth().clickable {
                onRead(manga, chapter.chapterNumber)
            }) {
                Row(Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(chapter.title?.takeIf { it.isNotBlank() }
                        ?: "Capítulo ${chapter.chapterNumber}", modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, contentDescription = "Ler")
                }
            }
        }
    }
}

@Composable
private fun ReaderView(
    vm: ReaderViewModel, screen: Screen.Reading,
    progress: ReadingProgress?, onChapter: (String) -> Unit
) {
    val chapter = vm.chapter
    if (vm.chapterLoading && chapter == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (chapter == null || chapter.chapterNumber != screen.chapter) {
        ErrorView(vm.chapterError ?: "Não foi possível abrir o capítulo.") {
            vm.loadChapter(screen.slug, screen.chapter)
        }
        return
    }
    val images = remember(chapter.id) { chapter.pages.sortedBy { it.pageNumber } }
    if (images.isEmpty()) {
        Text("Este capítulo não possui páginas disponíveis.",
            modifier = Modifier.padding(24.dp))
        return
    }
    val initial = if (progress?.chapterNumber == screen.chapter)
        progress.pageIndex.coerceIn(0, images.lastIndex) else 0
    key(chapter.id) {
        val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
        LaunchedEffect(state, chapter.id) {
            snapshotFlow { state.firstVisibleItemIndex }.distinctUntilChanged().collect { index ->
                vm.saveProgress(screen.id, screen.slug, screen.title, screen.coverUrl,
                    screen.chapter, index.coerceIn(0, images.lastIndex))
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cap. ${chapter.chapterNumber}", fontWeight = FontWeight.Bold)
                Text("${(state.firstVisibleItemIndex + 1).coerceAtMost(images.size)}/${images.size} páginas")
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                items(images, key = { it.id }) { image ->
                    AsyncImage(model = image.imageUrl,
                        contentDescription = "Página ${image.pageNumber}",
                        modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                }
                item {
                    Row(Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(enabled = chapter.prevChapter != null,
                            onClick = { chapter.prevChapter?.chapterNumber?.let(onChapter) }) {
                            Text("Anterior")
                        }
                        Button(enabled = chapter.nextChapter != null,
                            onClick = { chapter.nextChapter?.chapterNumber?.let(onChapter) }) {
                            Text("Próximo")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.WarningAmber, contentDescription = null,
            tint = MaterialTheme.colorScheme.error)
        Text(message)
        OutlinedButton(onClick = retry) { Text("Tentar novamente") }
    }
}
