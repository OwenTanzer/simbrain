"""Join the pinned FlyWire v783 annotations to the Shiu model's neuron order."""

import argparse
import csv
import gzip
import hashlib
import io
import json
from pathlib import Path
import urllib.request


SHIU_REVISION = "91bdd1e7dcf193f3e7ca5a8933497fcef63b7960"
SHIU_SHA256 = "bbb847a4cc2caaa7a16349722d220c087317b946d148d4d592d94d250617a311"
ANNOTATION_REVISION = "ebd66db2596fcc39c6950fb54ea3efa00f7fe8a0"  # v2.1.0
ANNOTATION_SHA256 = "30be6c73975a70c56d930e27911f36455d3886e15abf383b78edd2a5d679e0b6"
ANNOTATION_PATH = "supplemental_files/Supplemental_file1_neuron_annotations.tsv"


def checked_source(path, url, digest):
    path.parent.mkdir(parents=True, exist_ok=True)
    if not path.exists():
        urllib.request.urlretrieve(url, path)
    actual = hashlib.sha256(path.read_bytes()).hexdigest()
    if actual != digest:
        raise ValueError(f"Checksum mismatch for {path}: {actual}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("build/flybrain-source"))
    parser.add_argument("--output", type=Path, default=Path("simulations/data/flybrain/annotations-v783.tsv.gz"))
    parser.add_argument("--report", type=Path, default=Path("tools/flybrain/annotation-coverage-v783.json"))
    args = parser.parse_args()

    completeness = args.source / "Completeness_783.csv"
    annotations = args.source / "Supplemental_file1_neuron_annotations.tsv"
    checked_source(
        completeness,
        f"https://raw.githubusercontent.com/philshiu/Drosophila_brain_model/{SHIU_REVISION}/Completeness_783.csv",
        SHIU_SHA256,
    )
    checked_source(
        annotations,
        f"https://raw.githubusercontent.com/flyconnectome/flywire_annotations/{ANNOTATION_REVISION}/{ANNOTATION_PATH}",
        ANNOTATION_SHA256,
    )

    with completeness.open(newline="") as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or reader.fieldnames[0] != "":
            raise ValueError("Unexpected completeness ID column")
        ids = [row[""] for row in reader]
    if len(ids) != 138639 or len(set(ids)) != len(ids) or not all(i.isdecimal() for i in ids):
        raise ValueError("Unexpected model neuron IDs")

    with annotations.open(newline="") as stream:
        reader = csv.DictReader(stream, delimiter="\t")
        fields = reader.fieldnames
        if not fields or "root_id" not in fields or len(fields) != len(set(fields)):
            raise ValueError("Unexpected annotation columns")
        rows = {}
        for row in reader:
            root_id = row["root_id"]
            if not root_id.isdecimal() or root_id in rows or None in row:
                raise ValueError(f"Invalid or duplicate annotation ID: {root_id}")
            rows[root_id] = row

    missing = set(ids) - rows.keys()
    if missing:
        raise ValueError(f"Annotation rows missing for {len(missing)} model IDs; sample: {sorted(missing)[:10]}")
    extra = rows.keys() - set(ids)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as compressed:
            with io.TextIOWrapper(compressed, encoding="utf-8", newline="") as text:
                writer = csv.DictWriter(text, fieldnames=fields, delimiter="\t", lineterminator="\n")
                writer.writeheader()
                writer.writerows(rows[root_id] for root_id in ids)

    coverage = {field: sum(bool(rows[root_id][field]) for root_id in ids) for field in fields if field != "root_id"}
    report = {
        "model_source": f"https://github.com/philshiu/Drosophila_brain_model/tree/{SHIU_REVISION}",
        "model_ids_sha256": SHIU_SHA256,
        "annotation_source": f"https://github.com/flyconnectome/flywire_annotations/blob/{ANNOTATION_REVISION}/{ANNOTATION_PATH}",
        "annotation_release": "v2.1.0",
        "annotation_sha256": ANNOTATION_SHA256,
        "model_neurons": len(ids),
        "annotation_rows": len(rows),
        "matched_model_neurons": len(ids),
        "missing_model_neurons": 0,
        "extra_annotation_rows": len(extra),
        "extra_annotation_ids_sample": sorted(extra)[:10],
        "nonempty_model_fields": coverage,
        "output_sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
        "output_format": "gzip TSV in the Shiu v783 model's neuron order; root_id is the join key",
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
