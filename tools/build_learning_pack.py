"""Reproducible offline pack compiler. No model, API or third-party Python dependency.
Inputs are pinned in content/learning-pack-manifest.json. See docs/learning-pack.md.
MorphyNet derivative outputs retain CC BY-SA 3.0; other sources retain their notices.
"""
import argparse
from collections import defaultdict, Counter
from functools import lru_cache
import gzip
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
ASCII = re.compile(r"[a-z]{3,40}\Z")
POS = {"N": "n", "V": "v", "J": "adj", "R": "adv"}
# Dataset edges are evidence, not permission to introduce arbitrary one-letter affixes.
PREFIXES = set("un re in im il ir dis non mis pre post over under out up down fore anti auto bi tri multi inter intra trans super sub semi extra co de en em counter mid kilo micro macro mono poly neo bio astro photo psycho thermo stereo after grand with".split())
SUFFIXES = set("s es ed ing er est ly ness ment tion ion ation ition sion al ial ual ic ical ics icality ity ty ance ence ancy ency ant ent able ible ably ibly ful less ous ious eous ive ative itive y en ize ise ify ism ist or ee eer ess hood ship dom age ary ery ory ian an ean ese ure ate some ward wards wise fold proof man men side work land ware craft kind scape ry ette ster logy ology graphy istic atic icity".split())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sources", type=Path, required=True)
    args = parser.parse_args()
    src = args.sources
    out = ROOT / "app/src/main/resources/learning"
    out.mkdir(parents=True, exist_ok=True)
    manifest_path = ROOT / "content/learning-pack-manifest.json"
    raw_names = ["morphynet-eng.tsv", "cmudict.dict", "moby-hyphenation.txt"]
    hashes = {name: hashlib.sha256((src / name).read_bytes()).hexdigest() for name in raw_names}
    if manifest_path.exists():
        expected = json.loads(manifest_path.read_text(encoding="utf-8"))["inputSha256"]
        if hashes != expected:
            raise ValueError("Source hashes differ from pinned manifest; review an explicit pack version update")
    # Share the app's exception list; exceptions suppress analyses, not lexical evidence for bases.
    kotlin = (ROOT / "app/src/main/java/com/example/data/MorphologyRules.kt").read_text(encoding="utf-8")
    excluded = set(re.findall(r'"([a-z]+)"', kotlin.split("private val excluded = setOf(", 1)[1].split(")", 1)[0]))
    excluded |= {line.strip() for line in (ROOT / "content/morphology-exclusions.txt").read_text(encoding="utf-8").splitlines()
                 if line.strip() and not line.startswith("#")}
    pron = {}
    for line in (src / "cmudict.dict").read_text(encoding="utf-8").splitlines():
        tokens = line.partition("#")[0].split()
        if not tokens:
            continue
        word, *phones = tokens
        if not ASCII.fullmatch(word) or word in pron:
            continue
        vowels = [p for p in phones if re.fullmatch(r"(?:AA|AE|AH|AO|AW|AY|EH|ER|EY|IH|IY|OW|OY|UH|UW)[012]", p)]
        if vowels:
            pron[word] = (len(vowels), ",".join(str(i + 1) for i, p in enumerate(vowels) if p.endswith("1")))
    graph = defaultdict(set)
    rejection = Counter()
    for line in (src / "morphynet-eng.tsv").read_text(encoding="utf-8-sig").splitlines():
        fields = line.split("\t")
        if len(fields) != 6:
            rejection["malformed"] += 1
            continue
        base, word, bp, wp, affix, kind = fields
        if not ASCII.fullmatch(base) or not ASCII.fullmatch(word) or not re.fullmatch("[a-z]{1,10}", affix) or bp not in POS or wp not in POS:
            rejection["alphabet_or_pos"] += 1
            continue
        if word in excluded or word == base or kind not in ("prefix", "suffix"):
            rejection["exception"] += 1
            continue
        if base not in pron:
            rejection["base_absent_from_reference_dictionary"] += 1
            continue
        if affix not in (PREFIXES if kind == "prefix" else SUFFIXES):
            rejection["unsupported_affix"] += 1
            continue
        if kind == "prefix":
            if bp != wp or word != affix + base:
                rejection["prefix_pos_or_spelling"] += 1
                continue
            surface = base
        else:
            if not word.endswith(affix):
                rejection["suffix_boundary"] += 1
                continue
            surface = word[:-len(affix)]
            allowed = {base}
            if base.endswith("e") and affix[0] in "aeiouy":
                allowed.add(base[:-1])
            if base.endswith("y") and base[-2] not in "aeiou" and affix[0] != "i":
                allowed.add(base[:-1] + "i")
            if base == "day" and word == "daily" and affix == "ly":
                allowed.add("dai")
            if base.endswith("y") and affix in {"ize", "ise", "ist", "ify"}:
                allowed.add(base[:-1])
            if base.endswith("ie") and affix == "ing":
                allowed.add(base[:-2] + "y")
            if len(base) >= 3 and base[-1] not in "aeiouwxy" and base[-2] in "aeiou" and affix in {"ing", "ed", "er", "est", "en"}:
                allowed.add(base + base[-1])
            if base.endswith("le") and affix == "ly":
                allowed.add(base[:-2])
            if base.endswith("ble") and affix == "ity":
                allowed.add(base[:-2] + "il")
            if base.endswith("ify") and affix == "ation":
                allowed.add(base[:-1] + "ic")
            if base.endswith(("cide", "vide", "clude")) and affix == "ion":
                allowed.add(base[:-2] + "s")
            if base.endswith("mit") and affix == "ion":
                allowed.add(base[:-1] + "ss")
            if surface not in allowed or len(surface) < 2:
                rejection["unsupported_spelling"] += 1
                continue
        graph[(word, POS[wp])].add((base, POS[bp], affix, kind, surface))

    @lru_cache(maxsize=None)
    def resolve(word, pos, depth=0):
        if depth >= 3:
            return None
        candidates = {}
        for base, bp, affix, kind, surface in sorted(graph.get((word, pos), ())):
            inner = resolve(base, bp, depth + 1)
            parts = list(inner[0]) if inner else [base]
            # End spelling changes may touch the last leaf only. Otherwise keep the base intact.
            if surface != base:
                head = "".join(parts[:-1])
                if surface.startswith(head) and len(surface) > len(head):
                    parts[-1] = surface[len(head):]
                else:
                    parts = [surface]
            parts = ([affix] + parts) if kind == "prefix" else (parts + [affix])
            if not 2 <= len(parts) <= 6 or "".join(parts) != word:
                continue
            step = f"{base} → {word}" + (f"（拼写：{base} → {surface}）" if surface != base else "")
            chain = (inner[1] + "；" if inner else "") + step
            origin = inner[2] if inner else (base, bp)
            candidates.setdefault(tuple(parts), []).append((chain, origin))
        # Agree on both blocks and lexical origin (e.g. sing/singe cannot share an arbitrary explanation).
        if len(candidates) != 1:
            return None
        parts, paths = next(iter(candidates.items()))
        if len({p[1] for p in paths}) != 1:
            return None
        return parts, paths[0][0], paths[0][1]

    morph = []
    for word, pos in sorted(graph):
        resolved = resolve(word, pos)
        if resolved:
            parts, chain, _ = resolved
            morph.append("\t".join((word, pos, "|".join(parts), chain)))

    hyph = defaultdict(set)
    for line in (src / "moby-hyphenation.txt").read_bytes().decode("latin-1").splitlines():
        parts = line.split("\xa5")
        word = "".join(parts)
        if ASCII.fullmatch(word) and 2 <= len(parts) <= 6 and all(parts) and word in pron and pron[word][0] >= 2:
            hyph[word].add(tuple(parts))
    spelling_rows = ["\t".join((w, "|".join(next(iter(p))), str(pron[w][0]), pron[w][1]))
                     for w, p in sorted(hyph.items()) if len(p) == 1]
    for name, lines in [("morphynet.tsv.gz", morph), ("spelling.tsv.gz", spelling_rows)]:
        payload = ("\n".join(lines) + "\n").encode("utf-8")
        (out / name).write_bytes(gzip.compress(payload, mtime=0))
    notices = ROOT / "app/src/main/resources/learning/licenses"
    notices.mkdir(parents=True, exist_ok=True)
    for source, dest in [("cmudict-LICENSE.txt", "CMUdict.txt"), ("moby-NOTICE.txt", "Moby.txt"), ("CC-BY-SA-3.0.txt", "CC-BY-SA-3.0.txt")]:
        (notices / dest).write_bytes((src / source).read_bytes())
    manifest = {
        "version": "2026.10.02.2", "inputSha256": hashes,
        "morphynetCommit": (src / "morphynet-commit.txt").read_text(encoding="utf-8-sig").strip(),
        "cmudictCommit": (src / "cmudict-commit.txt").read_text(encoding="utf-8-sig").strip(),
        "morphologyRecords": len(morph), "spellingRecords": len(spelling_rows),
        "filterCounts": dict(sorted(rejection.items())),
        "outputSha256": {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(out.glob("*.gz"))},
    }
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (out / "manifest.json").write_bytes(manifest_path.read_bytes())
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
