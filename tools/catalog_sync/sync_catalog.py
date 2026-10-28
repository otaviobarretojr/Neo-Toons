#!/usr/bin/env python3
"""Neo-Toons public catalogue metadata backup.

Read-only. Downloads neither cover images nor chapter page images.
Uses the public API documented in auditoria-neotoons-android, GET only.
No cookies, credentials, scraping tricks, or rate-limit workarounds.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import sqlite3
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlencode

BASE_URL = "https://api.neotoons.com.br"
PAGE_LIMIT = 24
MAX_REASONABLE_PAGES = 2500
FIELDS = (
    "id", "slug", "title", "alternativeTitles", "synopsis",
    "coverUrl", "bannerUrl", "author", "artist", "status", "demographic",
    "releaseYear", "viewsCount", "ratingAverage", "ratingsCount",
    "latestChapterAt", "createdAt", "updatedAt"
)
CHAPTER_FIELDS = ("id", "chapterNumber", "createdAt", "releasedAt")


class ApiError(Exception):
    pass


def fetch_json(url: str) -> object:
    """One ordinary GET; abort on restricted responses instead of evading them."""
    command = [
        "curl", "--silent", "--show-error", "--location", "--max-time", "40",
        "--connect-timeout", "12", "--header", "Accept: application/json",
        "--write-out", "\n__NEOTOONS_HTTP_STATUS__:%{http_code}", url,
    ]
    try:
        response = subprocess.run(command, capture_output=True, timeout=46, check=False)
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise ApiError("Não foi possível executar a requisição: " + str(exc)) from exc
    marker = b"\n__NEOTOONS_HTTP_STATUS__:"
    if marker not in response.stdout:
        stderr = response.stderr.decode("utf-8", errors="replace").strip()
        raise ApiError("Falha na conexão HTTPS: " + (stderr[:300] or "sem status HTTP"))
    body, raw_status = response.stdout.rsplit(marker, 1)
    try:
        status = int(raw_status.decode("ascii").strip())
    except ValueError as exc:
        raise ApiError("Status HTTP inválido") from exc
    if status != 200:
        label = (
            "Acesso negado; interrompendo sem contornar proteções"
            if status in (401, 403) else
            "Limite de consultas; interrompendo sem insistir"
            if status == 429 else
            "Resposta inesperada do servidor"
        )
        raise ApiError(f"{label} (HTTP {status})")
    if response.returncode:
        raise ApiError(f"Falha no transporte HTTP (curl {response.returncode})")
    try:
        return json.loads(body.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise ApiError("A resposta não é um JSON válido") from exc


def normalize_work(item: dict) -> dict:
    work = {name: item.get(name) for name in FIELDS}
    if not all(isinstance(work.get(name), str) and work[name].strip()
               for name in ("id", "slug", "title")):
        raise ApiError("Obra sem id/slug/título válido: " + str(item)[:160])
    genres = item.get("genres")
    work["genres"] = [
        {"id": g.get("id"), "slug": g.get("slug"), "name": g.get("name")}
        for g in (genres if isinstance(genres, list) else [])
        if isinstance(g, dict)
    ]
    counts = item.get("_count")
    work["_count"] = {
        "chapters": counts.get("chapters"),
        "bookmarks": counts.get("bookmarks"),
    } if isinstance(counts, dict) else {}
    chapters = item.get("chapters")
    work["chapters"] = [
        {field: chapter.get(field) for field in CHAPTER_FIELDS}
        for chapter in (chapters if isinstance(chapters, list) else [])
        if isinstance(chapter, dict) and chapter.get("id")
    ]
    return work


def write_json(path: Path, value: object) -> None:
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n",
                         encoding="utf-8")
    temporary.replace(path)


def write_database(path: Path, works: list[dict], genres: list[dict],
                   report: dict) -> None:
    temporary = path.with_suffix(".tmp")
    temporary.unlink(missing_ok=True)
    with sqlite3.connect(temporary) as database:
        database.executescript("""
            PRAGMA foreign_keys = ON;
            CREATE TABLE works (
                id TEXT PRIMARY KEY, slug TEXT NOT NULL UNIQUE,
                title TEXT NOT NULL, author TEXT, synopsis TEXT,
                cover_url TEXT, status TEXT, demographic TEXT,
                latest_chapter_at TEXT, updated_at TEXT,
                chapter_count INTEGER, source_json TEXT NOT NULL
            );
            CREATE INDEX idx_works_title ON works(title);
            CREATE INDEX idx_works_latest ON works(latest_chapter_at);
            CREATE TABLE genres (
                slug TEXT PRIMARY KEY, name TEXT NOT NULL, declared_count INTEGER
            );
            CREATE TABLE work_genres (
                work_id TEXT NOT NULL REFERENCES works(id),
                genre_slug TEXT NOT NULL, PRIMARY KEY(work_id, genre_slug)
            );
            CREATE TABLE chapter_summaries (
                id TEXT PRIMARY KEY, work_id TEXT NOT NULL REFERENCES works(id),
                number TEXT, released_at TEXT
            );
            CREATE TABLE sync_metadata(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        """)
        for genre in genres:
            if not isinstance(genre, dict) or not genre.get("slug"):
                continue
            genre_count = genre.get("_count")
            database.execute("INSERT OR REPLACE INTO genres VALUES(?,?,?)",
                             (genre["slug"], genre.get("name") or genre["slug"],
                              genre_count.get("mangas") if isinstance(genre_count, dict) else None))
        for work in works:
            count = work.get("_count") or {}
            database.execute("INSERT INTO works VALUES(?,?,?,?,?,?,?,?,?,?,?,?)", (
                work["id"], work["slug"], work["title"], work.get("author"),
                work.get("synopsis"), work.get("coverUrl"), work.get("status"),
                work.get("demographic"), work.get("latestChapterAt"),
                work.get("updatedAt"), count.get("chapters"),
                json.dumps(work, ensure_ascii=False, separators=(",", ":"))
            ))
            for genre in work.get("genres", []):
                if genre.get("slug"):
                    database.execute("INSERT OR IGNORE INTO work_genres VALUES(?,?)",
                                     (work["id"], genre["slug"]))
            for chapter in work.get("chapters", []):
                database.execute("INSERT OR IGNORE INTO chapter_summaries VALUES(?,?,?,?)",
                                 (chapter["id"], work["id"], chapter.get("chapterNumber"),
                                  chapter.get("releasedAt")))
        for key, value in report.items():
            database.execute("INSERT INTO sync_metadata VALUES(?,?)",
                             (key, json.dumps(value, ensure_ascii=False)))
        database.commit()
        check = database.execute("PRAGMA integrity_check").fetchone()[0]
        if check != "ok":
            raise RuntimeError("SQLite integrity_check: " + check)
    temporary.replace(path)


def write_outputs(out: Path, works: list[dict], genres: list[dict],
                  report: dict) -> None:
    out.mkdir(parents=True, exist_ok=True)
    document = {
        "schema_version": 1,
        "source": "NeoToons public API",
        "metadata_only": True,
        "complete": report["complete"],
        "generated_at_utc": report["generated_at_utc"],
        "reported_total": report["reported_total"],
        "pages_fetched": report["pages_fetched"],
        "works": works,
    }
    write_json(out / "catalog.json", document)
    write_json(out / "genres.json", genres)
    write_json(out / "report.json", report)
    write_database(out / "catalog.sqlite3", works, genres, report)
    lines = [
        "# Neo-Toons — backup de metadados",
        "",
        f"Gerado em UTC: {report['generated_at_utc']}",
        f"Status: {'COMPLETO' if report['complete'] else 'PARCIAL'}",
        f"Obras únicas: {report['unique_works']}",
        f"Total anunciado: {report['reported_total']}",
        f"Páginas consultadas: {report['pages_fetched']}/{report['reported_pages']}",
        f"Gêneros: {report['genres']}",
        f"Duplicados entre páginas: {report['duplicates']}",
        f"Requisições: {report['requests']}",
        "",
        "Somente metadados públicos e resumos de capítulos; nenhuma imagem foi baixada.",
        "As listas de capítulos do catálogo podem ser incompletas (apenas resumos).",
        "Este backup NÃO contém todos os capítulos de cada obra.",
        "Não use um snapshot parcial como catálogo completo.",
    ]
    if report.get("error"):
        lines.extend(["", "## Erro", "", report["error"]])
    if report.get("warnings"):
        lines.extend(["", "## Alertas", ""] +
                     ["- " + w for w in report["warnings"]])
    (out / "README.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    hashes = []
    for path in sorted(out.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS.txt":
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            hashes.append(digest + "  " + path.name)
    (out / "SHA256SUMS.txt").write_text("\n".join(hashes) + "\n", encoding="utf-8")


def run_sync(out: Path, base_url: str = BASE_URL, page_limit: int = PAGE_LIMIT,
             delay: float = 1.25, max_pages: int = 0) -> dict:
    if page_limit < 1 or page_limit > 24:
        raise ValueError("limit deve estar entre 1 e 24")
    if delay < 0:
        raise ValueError("delay deve ser >= 0")
    if max_pages < 0:
        raise ValueError("max_pages deve ser >= 0")
    base_url = base_url.rstrip("/")
    if not base_url.startswith("https://"):
        raise ValueError("API exige HTTPS")
    works_by_id: dict[str, dict] = {}
    genres: list[dict] = []
    error = None
    warnings: list[str] = []
    duplicates = 0
    pages_fetched = 0
    reported_total = 0
    reported_pages = 0
    requests = 0
    start = time.monotonic()
    try:
        requests += 1
        raw_genres = fetch_json(base_url + "/genres")
        if not isinstance(raw_genres, list):
            raise ApiError("Resposta de gêneros inesperada")
        genres = [g for g in raw_genres if isinstance(g, dict)]
        page = 1
        while True:
            if page > 1 and delay:
                time.sleep(delay)
            url = base_url + "/mangas?" + urlencode({
                "page": page, "limit": page_limit, "sortBy": "newest"
            })
            requests += 1
            response = fetch_json(url)
            if not isinstance(response, dict) or not isinstance(response.get("items"), list):
                raise ApiError("Resposta do catálogo não contém items[]")
            total = response.get("total")
            total_pages = response.get("totalPages")
            if not isinstance(total, int) or not isinstance(total_pages, int) or total < 0:
                raise ApiError("Contagem do catálogo inválida")
            if total_pages < 1 or total_pages > MAX_REASONABLE_PAGES:
                raise ApiError("Quantidade de páginas fora do limite de segurança")
            if page == 1:
                reported_total, reported_pages = total, total_pages
            elif reported_total != total or reported_pages != total_pages:
                warning = f"Contagem mudou na página {page}: {total}/{total_pages}"
                if warning not in warnings:
                    warnings.append(warning)
            for item in response["items"]:
                if not isinstance(item, dict):
                    raise ApiError("Obra com formato inesperado")
                work = normalize_work(item)
                if work["id"] in works_by_id:
                    duplicates += 1
                works_by_id[work["id"]] = work
            pages_fetched += 1
            print(f"Página {page}/{reported_pages}: {len(response['items'])} itens; "
                  f"{len(works_by_id)} únicos", flush=True)
            if page >= reported_pages or (max_pages and page >= max_pages):
                break
            page += 1
    except (ApiError, ValueError, OSError, sqlite3.Error) as exc:
        error = str(exc)
    works = list(works_by_id.values())
    intended_full = max_pages == 0 or (reported_pages > 0 and max_pages >= reported_pages)
    complete = (
        error is None and intended_full and pages_fetched == reported_pages and
        len(works) == reported_total and not warnings
    )
    if not error and intended_full and not complete:
        warnings.append("A contagem final não corresponde ao total anunciado; snapshot parcial.")
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "complete": complete, "unique_works": len(works),
        "reported_total": reported_total, "reported_pages": reported_pages,
        "pages_fetched": pages_fetched, "genres": len(genres),
        "duplicates": duplicates, "requests": requests,
        "duration_seconds": round(time.monotonic() - start, 2),
        "warnings": warnings, "error": error,
    }
    write_outputs(out, works, genres, report)
    if error:
        raise ApiError(error)
    if intended_full and not complete:
        raise ApiError("Backup incompleto: veja report.json e README.md")
    return report


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", default="build/catalog-backup")
    parser.add_argument("--base-url", default=BASE_URL)
    parser.add_argument("--delay-seconds", type=float, default=1.25)
    parser.add_argument("--max-pages", type=int, default=0,
                        help="0 = catálogo completo; N > 0 = amostra controlada")
    args = parser.parse_args()
    try:
        report = run_sync(Path(args.output_dir), base_url=args.base_url,
                          delay=args.delay_seconds, max_pages=args.max_pages)
        print(json.dumps(report, ensure_ascii=False, indent=2))
        return 0
    except (ApiError, ValueError) as exc:
        print(f"BACKUP INCOMPLETO: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
