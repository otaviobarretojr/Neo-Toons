# Neo-Toons Android (MVP 0.1.0)

Android nativo em **Kotlin + Jetpack Compose**, usando Retrofit para a API pública auditada da NeoToons (`https://api.neotoons.com.br/`).

**Funções do MVP:** catálogo paginado, pesquisa, filtro por gênero e status, ordenação, detalhes e capítulos, leitor vertical, favoritos e progresso local com Room. Sem login ou anúncios próprios.

**Build:** em GitHub Actions, abra `Actions > Build Android Debug APK` e acione o workflow, ou aguarde o build no push para `main`. Se aprovado, baixe o artefato `Neo-Toons-debug-apk`.

A integração depende de autorização para acessar e apresentar o conteúdo fornecido pela API e seus hosts de imagem. Não há cookies ou tokens de sessão no código; não contorna bloqueios. Build e testes funcionais Android ainda precisam de validação.

## 0.1.1 — Leitor e imagens

- Reserva altura para imagens de capítulos, evitando páginas invisíveis no carregamento.
- Adapta a altura à proporção real após o download.
- Mostra mensagem e botão de tentar novamente quando a origem falha.
- Link de fallback para o leitor oficial, sem contornar restrições.
- Favoritos e progresso local mantidos; evita salvar página que não carregou.
- Capas com indicador de falha e sinopses recolhidas por padrão.

A compatibilidade com hosts externos de imagens depende das políticas de cada host e requer teste no dispositivo.

## APK de testes instalável em paralelo

Os APKs debug do GitHub Actions podem receber certificados diferentes a cada execução. Para preservar a instalação e os favoritos do app original, o tipo `debug` agora usa o pacote independente `br.com.neotoons.reader.beta`, com o nome **Neo-Toons Beta**. O APK pode ser instalado ao lado do app original. Os dados locais das duas instalações são independentes. Para atualizações futuras com preservação de dados, configurar uma chave de assinatura estável guardada como segredo de CI.
