"""Offline tests for the NeoToons catalogue mirroring workflow."""
import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import sync_catalog as sync

def sample(i):
    return {
        "id": f"id-{i}", "slug": f"obra-{i}", "title": f"Obra {i}",
        "coverUrl": f"https://example.org/{i}.webp",
        "synopsis": "Resumo",
        "_count": {"chapters": 42, "bookmarks": 3},
        "chapters": [{"id": f"ch-{i}", "chapterNumber": "1.5"}],
        "genres": [{"slug": "acao", "name": "Ação"}]
    }

def fake_fetch(url):
    if url.endswith("/genres"):
        return [{"slug": "acao", "name": "Ação", "_count": {"mangas": 3}}]
    if "page=1" in url:
        return {"items": [sample(1), sample(2)], "total": 3,
                "page": 1, "totalPages": 2}
    if "page=2" in url:
        return {"items": [sample(3)], "total": 3,
                "page": 2, "totalPages": 2}
    raise AssertionError("URL inesperada: " + url)

class CatalogSyncTests(unittest.TestCase):
    def test_complete_generates_json_and_sqlite(self):
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            with patch.object(sync, "fetch_json", side_effect=fake_fetch):
                report = sync.run_sync(out, delay=0, page_limit=24)
            self.assertTrue(report["complete"])
            self.assertEqual(report["unique_works"], 3)
            self.assertEqual(report["requests"], 3)
            doc = json.loads((out / "catalog.json").read_text(encoding="utf-8"))
            self.assertEqual(len(doc["works"]), 3)
            self.assertTrue(doc["metadata_only"])
            self.assertEqual(doc["works"][0]["chapters"][0]["chapterNumber"], "1.5")
            with sqlite3.connect(out / "catalog.sqlite3") as db:
                self.assertEqual(db.execute("SELECT count(*) FROM works").fetchone()[0], 3)
                self.assertEqual(db.execute("SELECT count(*) FROM genres").fetchone()[0], 1)
                self.assertEqual(db.execute("SELECT count(*) FROM chapter_summaries").fetchone()[0], 3)
            self.assertTrue((out / "SHA256SUMS.txt").exists())

    def test_one_page_is_marked_partial_without_error(self):
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(sync, "fetch_json", side_effect=fake_fetch):
                report = sync.run_sync(Path(tmp), delay=0, max_pages=1)
            self.assertFalse(report["complete"])
            self.assertEqual(report["unique_works"], 2)
            self.assertIsNone(report["error"])

    def test_restricted_api_stops_and_preserves_partial_snapshot(self):
        def restricted(url):
            if "page=2" in url:
                raise sync.ApiError("Acesso negado; interrompendo sem contornar proteções (HTTP 403)")
            return fake_fetch(url)
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(sync, "fetch_json", side_effect=restricted):
                with self.assertRaises(sync.ApiError):
                    sync.run_sync(Path(tmp), delay=0)
            self.assertFalse(json.loads((Path(tmp) / "report.json").read_text())["complete"])
            self.assertTrue((Path(tmp) / "catalog.sqlite3").exists())

    def test_duplicates_do_not_inflate_total(self):
        def duplicate(url):
            response = fake_fetch(url)
            if "page=2" in url:
                response["items"] = [sample(2)]
            return response
        with tempfile.TemporaryDirectory() as tmp:
            with patch.object(sync, "fetch_json", side_effect=duplicate):
                with self.assertRaises(sync.ApiError):
                    sync.run_sync(Path(tmp), delay=0)
            report = json.loads((Path(tmp) / "report.json").read_text())
            self.assertFalse(report["complete"])
            self.assertEqual(report["duplicates"], 1)

    def test_normalization_omits_private_fields(self):
        item = sample(1)
        item["uploader"] = {"email": "private@example.org"}
        item["authToken"] = "secret"
        work = sync.normalize_work(item)
        self.assertNotIn("uploader", work)
        self.assertNotIn("authToken", work)
        self.assertIn("coverUrl", work)

if __name__ == "__main__":
    unittest.main()
