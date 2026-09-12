"""Convert the pinned Shiu/FlyWire v783 tables to a checked sparse Simbrain asset."""
import argparse
import gzip
import hashlib
import json
from pathlib import Path
import struct
import urllib.request

import numpy as np
import pandas as pd

REVISION = "91bdd1e7dcf193f3e7ca5a8933497fcef63b7960"
FILES = {
    "Completeness_783.csv": "bbb847a4cc2caaa7a16349722d220c087317b946d148d4d592d94d250617a311",
    "Connectivity_783.parquet": "efeb23fb99098e9c390f6869969b2a121a2ee92c833cfc45ecb2c1d8e1af0347",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("build/flybrain-source"))
    parser.add_argument("--output", type=Path, default=Path("simulations/data/flybrain/flywire-v783.bin.gz"))
    args = parser.parse_args()
    args.source.mkdir(parents=True, exist_ok=True)
    for name, digest in FILES.items():
        path = args.source / name
        if not path.exists():
            url = f"https://raw.githubusercontent.com/philshiu/Drosophila_brain_model/{REVISION}/{name}"
            urllib.request.urlretrieve(url, path)
        if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
            raise ValueError(f"Checksum mismatch: {name}")
    ids = pd.read_csv(args.source / "Completeness_783.csv", index_col=0).index.to_numpy(dtype=np.int64)
    table = pd.read_parquet(args.source / "Connectivity_783.parquet")
    pre = table.Presynaptic_Index.to_numpy()
    order = np.argsort(pre, kind="stable")
    offsets = np.r_[0, np.cumsum(np.bincount(pre, minlength=len(ids)))].astype(">i4")
    targets = table.Postsynaptic_Index.to_numpy()[order].astype(">i4")
    weights = (table["Excitatory x Connectivity"].to_numpy()[order] * 0.275).astype(">f8")
    assert len(ids) == 138639 and len(targets) == 15091983
    assert targets.min() >= 0 and targets.max() < len(ids)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open("wb") as raw, gzip.GzipFile(fileobj=raw, mode="wb", filename="", mtime=0) as f:
        f.write(b"SBFLY001")
        f.write(struct.pack(">ii", len(ids), len(targets)))
        for array in (ids.astype(">i8"), offsets, targets, weights):
            f.write(array.tobytes())
    metadata = {
        "dataset": "FlyWire v783, female adult brain; Shiu et al. model tables",
        "source_repository": "https://github.com/philshiu/Drosophila_brain_model",
        "source_revision": REVISION,
        "source_sha256": FILES,
        "neurons": len(ids), "weighted_connections": len(targets),
        "anatomical_contacts": int(table.Connectivity.sum()),
        "weight_conversion": "Excitatory x Connectivity * 0.275 mV",
        "asset_sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
        "format": "gzip; SBFLY001; big-endian int32 counts, int64 IDs, int32 source offsets, int32 targets, float64 weights",
    }
    args.output.with_name("provenance.json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
