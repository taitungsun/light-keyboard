# Third-party data notice — Zhuyin candidate dictionary

`zh-bopomofo-chewing.dict.gz` is a derivative of the **libchewing** dictionary
data (詞庫 `tsi.csv` + 字庫 `word.csv`).

- Upstream: https://github.com/chewing/libchewing-data (`dict/chewing/`)
- Copyright (c) 2025 libchewing Core Team
- License: **LGPL-2.1-or-later**
- Data revision: `main` re-fetched 2026-07-11 (tsi 160,417 / word 26,059 rows)

The derivation (parse CSV → strip tone marks/spaces to a lookup key → rank words
by frequency → record each word's per-syllable tone-signature set for tone-aware
ranking → gzip) is performed by `ui/tools/build_zhuyin_dict.py`; re-run that
script against a fresh checkout of the upstream CSVs to regenerate this file.

This is bundled data, not linked code. Note that light-keyboard itself is MIT;
this asset carries libchewing's LGPL-2.1-or-later terms independently. Keep this
notice with the file, and review the licensing before upstreaming to an
MIT-only project.
