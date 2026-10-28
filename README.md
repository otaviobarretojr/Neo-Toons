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

## 0.1.2 — Compatibilidade de servidores de imagem

- Opção **Modo compatível** no leitor: usa Android WebView/Chromium somente para imagens HTTPS de capítulos, sem scripts ou armazenamento web.
- Diagnóstico de falhas com domínio e causa final do erro TLS.
- Em erro nativo oferece alternância para motor WebView.
- Nenhum certificado inválido é aceito: o WebView cancela erros SSL.
- O modo web não rastreia progresso de página (o progresso do leitor nativo continua).
- Pacote de testes `.beta2` para instalar ao lado da versão Beta anterior, que usa chave debug potencialmente diferente a cada build.

Se até o WebView não carregar a imagem, a origem HTTPS precisa ser corrigida pelo responsável pelo host ou pela integração autorizada; não se deve desativar verificação SSL.

## Exportação de catálogo pelo Android (Beta 0.1.3)

Como o GitHub Actions recebeu 403 na API (embora o celular já consiga consultar o catálogo), a versão Beta 0.1.3 acrescenta um exportador na própria tela do catálogo, no ícone de download.

1. Toque em **Exportar catálogo → Gerar backup**; mantenha o app aberto durante a coleta.
2. O exportador consulta uma página por vez com intervalo conservador de 1,25 s, sem baixar imagens.
3. Quando indicar **backup completo**, toque em **Salvar arquivo**.
4. O seletor de arquivos do Android permite salvar em Downloads ou Google Drive, quando esse provedor estiver disponível.
5. O arquivo somente é oferecido quando todas as obras foram recebidas, sem duplicações ou alteração do total. Em erro 401/403/429, não há contorno nem backup anunciado como completo.

O arquivo JSON contém gêneros, metadados públicos de obras e pequenos resumos de capítulos do catálogo. Não contém imagens nem o texto/páginas dos capítulos. A Beta 0.1.3 usa sufixo de pacote `.beta3` para instalar sem substituir as versões de teste anteriores.
