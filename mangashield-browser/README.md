# MangaShield — navegador Android para ler mangás

Aplicativo Android nativo (Java, WebView) com proteção contra recursos de anúncios,
janelas pop-up, rastreadores e elementos publicitários no HTML.

## Recursos
- Barra de endereço: cole URL ou pesquise por título.
- Navegação anterior, próxima, atualizar, favoritos locais, modo leitor em tela cheia.
- Bloqueio de sub-recursos por domínio, rotas publicitárias e Service Workers.
- Filtro cosmético de anúncios comum em páginas.
- Listas atualizadas automaticamente a cada 7 dias (AdAway e StevenBlack) e manualmente.
- Bloqueio ligado/desligado e exceção por domínio.
- Cookies de terceiros desativados, Safe Browsing ativo e ausência de permissões de câmera/microfone.
- Primeiro acesso pré-configurado para https://neotoons.com.br/obras.

## Como obter o APK
1. Acesse GitHub Actions no repositório Neo-Toons.
2. Selecione **Build MangaShield Android APK**.
3. Abra o último run bem-sucedido da branch `feature/mangashield-browser-android`.
4. Em *Artifacts*, baixe `MangaShield-debug-apk` e extraia o APK.
5. Instale o APK no Android permitindo instalação dessa origem, se solicitado.

O APK gerado é **debug assinado automaticamente pelo SDK** para teste pessoal;
não é uma release assinada para publicação em loja.

## Compilar manualmente
Requisitos: JDK 17, Android SDK 35, Build Tools 35.0.0, Gradle 8.9.

```bash
cd mangashield-browser
gradle assembleDebug
```

Saída: `app/build/outputs/apk/debug/app-debug.apk`

## Limitações conhecidas
- O WebView não oferece a mesma abrangência de uma extensão completa do uBlock Origin.
- `shouldInterceptRequest` não intercepta todos os esquemas (como blob:) e pode não ver URLs posteriores a redirecionamentos.
- Os filtros de domínio e os cosméticos podem falhar em anúncios servidos pelo domínio de conteúdo ou em layouts incomuns.
- Sites protegidos por sistemas anti-bot podem restringir o acesso em WebView.
- Pop-ups legítimos de login/compartilhamento que abrem janela nova também são bloqueados.
- Ao limpar dados de navegação, os favoritos continuam salvos.
- Não há captura ou download de capítulos; os conteúdos são exibidos a partir dos sites acessados.

## Privacidade
Sem conta, publicidade própria, analytics ou telemetria. Links e favoritos ficam no armazenamento privado do aparelho.
O aplicativo consulta as duas fontes públicas de filtros e carrega as páginas solicitadas pelo usuário.
A leitura de sites de terceiros deve observar seus termos e direitos autorais.
