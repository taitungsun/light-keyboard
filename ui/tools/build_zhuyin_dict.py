#!/usr/bin/env python3
"""
Build the light-keyboard Zhuyin candidate dictionary from libchewing's CSV data.

Input : tsi.csv (phrases+chars, real frequencies) and word.csv (char fallback,
        freq 0) — format `word,frequency,ㄅ ㄆ ㄇ ...` (space-separated toned syllables).
Output: one gzip'd text file, lines `key\tword1 word2 ...`, sorted by key, where
        key = the reading with tone marks and spaces removed (so the keyboard's
        space-less, tone-optional buffer matches directly). Words are ordered
        best-first by frequency.

Source: chewing/libchewing-data, dict/chewing/{tsi,word}.csv  (LGPL-2.1-or-later).
"""
import gzip
import sys

# Combining/standalone tone marks + spaces stripped from readings to form keys.
STRIP = set(" \tˉˊˇˋ˙")  # U+02C9 02CA 02C7 02CB 02D9

def is_cjk(ch: str) -> bool:
    o = ord(ch)
    return (0x3400 <= o <= 0x4DBF or 0x4E00 <= o <= 0x9FFF or
            0xF900 <= o <= 0xFAFF or 0x20000 <= o <= 0x2FA1F)

def has_cjk(word: str) -> bool:
    return any(is_cjk(c) for c in word)

def strip_key(reading: str) -> str:
    return "".join(c for c in reading if c not in STRIP)

def parse(path, buckets, order):
    n = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.rstrip("\n")
            if not line or line.startswith("#"):
                continue
            parts = line.split(",", 2)
            if len(parts) != 3:
                continue
            word, freq, reading = parts
            if not has_cjk(word):
                continue
            try:
                freq = int(freq)
            except ValueError:
                freq = 0
            key = strip_key(reading)
            if not key:
                continue
            b = buckets.setdefault(key, {})
            # Keep the highest frequency seen for a (key, word) pair; remember
            # first-seen order so equal-freq words keep a stable order.
            if word not in b or freq > b[word]:
                b[word] = freq
            order.setdefault((key, word), len(order))
            n += 1
    return n

def main():
    tsi, word, out = sys.argv[1], sys.argv[2], sys.argv[3]
    buckets = {}
    order = {}
    # tsi first so real frequencies win; word.csv only fills in missing chars.
    n1 = parse(tsi, buckets, order)
    n2 = parse(word, buckets, order)

    PER_KEY_CAP = 50
    lines = []
    total_words = 0
    for key in sorted(buckets):
        words = sorted(buckets[key], key=lambda w: (-buckets[key][w], order[(key, w)]))
        words = words[:PER_KEY_CAP]
        total_words += len(words)
        lines.append(key + "\t" + " ".join(words))

    blob = "\n".join(lines) + "\n"
    with gzip.open(out, "wt", encoding="utf-8", compresslevel=9) as g:
        g.write(blob)

    print(f"parsed tsi={n1} word={n2} lines")
    print(f"keys={len(lines)} words_emitted={total_words}")
    print(f"raw_bytes={len(blob.encode('utf-8'))}")
    # Spot checks
    idx = {l.split(chr(9))[0]: l.split(chr(9))[1] for l in lines}
    for k in ["ㄋㄧ", "ㄋㄧㄏㄠ", "ㄕ", "ㄒㄧㄝㄒㄧㄝ", "ㄨㄛ", "ㄓㄨㄥㄨㄣ"]:
        v = idx.get(k, "<MISSING>")
        print(f"  {k} -> {' '.join(v.split()[:8])}")

if __name__ == "__main__":
    main()
