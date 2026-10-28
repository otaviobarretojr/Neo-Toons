# Neo-Toons Android (MVP 0.1.0)

Android nativo em **Kotlin + Jetpack Compose**, usando Retrofit para a API pública auditada da NeoToons (`https://api.neotoons.com.br/`).

**Funções do MVP:** catálogo paginado, pesquisa, filtro por gênero e status, ordenação, detalhes e capítulos, leitor vertical, favoritos e progresso local com Room. Sem login ou anúncios próprios.

**Build:** em GitHub Actions, abra `Actions > Build Android Debug APK` e acione o workflow, ou aguarde o build no push para `main`. Se aprovado, baixe o artefato `Neo-Toons-debug-apk`.

A integração depende de autorização para acessar e apresentar o conteúdo fornecido pela API e seus hosts de imagem. Não há cookies ou tokens de sessão no código; não contorna bloqueios. Build e testes funcionais Android ainda precisam de validação.
