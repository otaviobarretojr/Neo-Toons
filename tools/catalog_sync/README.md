# Sincronização de metadados — Neo-Toons

Este módulo gera um **backup do catálogo público**, sem copiar arquivos de imagem, sem copiar capítulos completos e sem acessar contas de usuários.

## Fonte confirmada na auditoria

- `GET https://api.neotoons.com.br/genres`
- `GET https://api.neotoons.com.br/mangas?page=1&limit=24&sortBy=newest`
- Paginação pela resposta `totalPages`. A contagem muda; nunca fixar 237.
- Não presumir que os três capítulos resumidos do catálogo sejam a lista completa.

## Rodar no GitHub

Acesse **Actions → Backup Neo-Toons catalogue metadata**. O fluxo também é iniciado automaticamente quando o sincronizador é atualizado no GitHub. O modo padrão consulta todas as páginas com **1,25 segundo entre requisições**.

No final, baixe o artefato `Neo-Toons-catalogo-metadados`, que inclui:

- `catalog.json` — obras e metadados, com indicadores de completude;
- `genres.json` — gêneros retornados pela API;
- `catalog.sqlite3` — banco SQLite pesquisável e útil para o futuro app;
- `report.json`, `README.md` e `SHA256SUMS.txt`.

O backup fica nos **artefatos do GitHub Actions** (30 dias), não é automaticamente publicado no repositório público e não modifica o site original.

**Importante:** se houver HTTP 401, 403 ou 429, o sincronizador interrompe o acesso. Um backup parcial nunca é anunciado como completo. Dados podem variar durante a paginação; o resultado só é completo se a contagem corresponder ao total anunciado. A auditoria anterior indicou possível bloqueio a alguns clientes: o GitHub Actions ainda precisa ser testado.

## Testes

```sh
python -m unittest discover -s tools/catalog_sync/tests -v
```

## Próximas etapas

1. Validar conexão e gerar snapshot completo.
2. Definir local seguro e persistente para o arquivo (Drive opcional).
3. Adicionar deltas e sincronização das obras seguidas.
4. Configurar cache de imagens **somente** mediante autorização dos detentores e hosts.

O SQLite guarda IDs, slugs, títulos, capas (URLs), gêneros, contagens e resumos. **Não contém as imagens nem a lista integral dos capítulos de todas as obras.**
