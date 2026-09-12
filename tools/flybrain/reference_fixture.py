"""Generate an independent Brian2 oracle using the unmodified upstream create_model function."""
import argparse
import importlib.util
import json
from pathlib import Path
import tempfile

import brian2 as b
import numpy as np
import pandas as pd


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("source", type=Path)
    p.add_argument("output", type=Path)
    a = p.parse_args()
    spec = importlib.util.spec_from_file_location("upstream_fly_model", a.source / "model.py")
    upstream = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(upstream)
    b.prefs.codegen.target = "numpy"
    b.defaultclock.dt = 0.1 * b.ms
    edges = [(0, 1, 120), (1, 2, 160), (2, 1, -90), (2, 3, 100), (0, 3, 50), (3, 4, 60), (4, 1, 30)]
    inputs = {0: [0], 5: [0], 13: [0], 28: [0], 40: [0], 62: [0], 120: [0], 180: [0], 230: [0]}
    rows = []
    with tempfile.TemporaryDirectory() as root:
        root = Path(root)
        pd.DataFrame({"Completed": [True]*5}, index=np.arange(100, 105)).to_csv(root / "neurons.csv")
        pd.DataFrame(edges, columns=["Presynaptic_Index", "Postsynaptic_Index", "Excitatory x Connectivity"]).to_parquet(root / "edges.parquet")
        b.start_scope()
        neu, syn, monitor = upstream.create_model(root / "neurons.csv", root / "edges.parquet", upstream.default_params)
        neu.rfc[0] = 0 * b.ms
        drive = np.zeros((300, 5))
        for step, indices in inputs.items():
            drive[step, indices] = 68.75
        neu.namespace["drive"] = b.TimedArray(drive * b.mV, dt=b.defaultclock.dt)
        stimulus = neu.run_regularly("v += drive(t, i)", when="synapses", order=1)
        state = b.StateMonitor(neu, ["v", "g"], record=True, when="end")
        net = b.Network(neu, syn, monitor, stimulus, state)
        net.run(30 * b.ms)
        spikes = {(int(round(t / b.defaultclock.dt)), int(i)) for t, i in zip(monitor.t, monitor.i)}
        for step in range(300):
            for i in range(5):
                rows.append([step, i, float(state.v[i, step] / b.mV), float(state.g[i, step] / b.mV), int((step, i) in spikes)])
    a.output.parent.mkdir(parents=True, exist_ok=True)
    pd.DataFrame(rows, columns=["tick", "neuron", "v", "g", "spike"]).to_csv(a.output, index=False)
    a.output.with_suffix(".json").write_text(json.dumps({"brian2": b.__version__, "source_revision": "91bdd1e7dcf193f3e7ca5a8933497fcef63b7960", "dt_ms": .1, "edges_signed_counts": edges, "input_ticks": inputs, "spikes": sorted(spikes)}, indent=2)+"\n")
    print(f"Saved {len(rows)} oracle rows, {len(spikes)} spikes")


if __name__ == "__main__":
    main()
