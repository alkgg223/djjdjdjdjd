"""Add or replace translation keys: python3 add_lang.py keys.json  (keys.json = {"key": ["english", "arabic"]})"""
import json
import os
import sys

LANG = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gunsmod", "lang")


def add(entries):
    for name, idx in (("en_us", 0), ("ar_sa", 1), ("ar_iq", 1)):
        path = os.path.join(LANG, name + ".json")
        with open(path, encoding="utf-8") as fh:
            data = json.load(fh)
        for k, v in entries.items():
            data[k] = v[idx]
        with open(path, "w", encoding="utf-8") as fh:
            json.dump(data, fh, ensure_ascii=False, indent=1, sort_keys=True)
            fh.write("\n")


if __name__ == "__main__":
    with open(sys.argv[1], encoding="utf-8") as fh:
        add(json.load(fh))
