package br.com.neotoons.reader.ui

import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import br.com.neotoons.reader.data.MangaImage

/**
 * Modo alternativo de leitura: utiliza o mecanismo HTTPS do Chromium/WebView.
 * Exibe somente URLs HTTPS devolvidas pela API, sem JavaScript nem anúncios injetados.
 * Nunca ignora falhas de certificados.
 */
@Composable
fun BrowserChapterReader(
    pages: List<MangaImage>,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val html = remember(pages) {
        val content = pages
            .sortedBy { it.pageNumber }
            .filter { Uri.parse(it.imageUrl).scheme.equals("https", ignoreCase = true) }
            .joinToString(separator = "\n") { page ->
                val safeUrl = htmlEscape(page.imageUrl)
                "<img src=\"$safeUrl\" alt=\"Página ${page.pageNumber}\" loading=\"lazy\">"
            }
        """<!doctype html><html lang="pt-BR">
        <head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=5">
        <style>
          html, body { margin:0; padding:0; background:#16131b; color:white; }
          img { width:100%; height:auto; display:block; border:0; }
        </style></head><body>$content</body></html>"""
    }
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.rgb(22,19,27))
                settings.javaScriptEnabled = false
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.loadsImagesAutomatically = true
                settings.blockNetworkImage = false
                webViewClient = object : WebViewClient() {
                    override fun onReceivedSslError(
                        view: WebView?, handler: SslErrorHandler?, error: SslError?
                    ) {
                        handler?.cancel()
                        onError("Certificado HTTPS recusado em ${host(error?.url)}. Não será ignorado.")
                    }
                    override fun onReceivedError(
                        view: WebView?, request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        if (error?.errorCode == ERROR_FAILED_SSL_HANDSHAKE) {
                            onError("Falha HTTPS também no modo compatível: ${host(request?.url?.toString())}.")
                        }
                    }
                    override fun onReceivedHttpError(
                        view: WebView?, request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        if (request?.isForMainFrame == false &&
                            (errorResponse?.statusCode ?: 0) >= 400) {
                            onError("Imagem recusada por ${host(request.url.toString())} " +
                                "(HTTP ${errorResponse?.statusCode}).")
                        }
                    }
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean = true
                }
                loadDataWithBaseURL(
                    "https://neotoons.com.br/obras/",
                    html, "text/html", "UTF-8", null
                )
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.destroy()
        }
    )
}

private fun host(url: String?): String =
    url?.let { Uri.parse(it).host } ?: "origem desconhecida"

private fun htmlEscape(text: String): String =
    text.replace("&", "&amp;").replace("\"", "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
