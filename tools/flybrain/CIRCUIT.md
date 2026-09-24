# Feeding circuit explorer

Open **Explore feeding circuit** in the Flybrain experiment controls. The observer keeps the complete 138,639-neuron simulation running and initially displays a literature-identified 43-neuron feeding pathway: 20 retained sugar inputs; Clavicle, FMIn, G2N-1, Usnea, Phantom, Rattle, Bract, Fdg, Roundup and Zorro group members; and right MN9. It is a selected view, not an independently sufficient or complete feeding circuit.

## Explore

- Choose a preset neuron or enter an exact root ID and click **Inspect ID**. Selection does not reset the experiment or change the watched trace.
- **Anatomy** shows real arbors in physical XY, XZ or YZ projections. Units are micrometres; the vertical coordinate increases downward. Circles mark annotation anchors; outlined squares mark available somata. Twenty-two preset neurons have no soma coordinates.
- **Brain context** shows one in eight whole-brain annotation anchors. Disable it to fit the circuit arbors more closely. This cloud is contextual sampling, not a sampled simulation.
- **Connectivity** is a schematic grouped by alias. Blue solid and red dashed arrows show positive and negative canonical model weights. By default it highlights selected-neuron edges; **All internal edges** exposes the entire induced graph. The footer states the displayed/total count, including the 2,000-edge drawing limit. It does not suggest where physical synapses lie on arbors.
- Drag to pan; wheel to zoom; **Fit view** resets the camera.
- **Incoming** and **Outgoing** tables contain all connections for the selected neuron, including partners outside the preset. Sort by a column heading, scroll horizontally if necessary, and double-click a row to inspect its partner. Root IDs remain exact decimal strings.
- **Contacts** are unsigned source anatomical counts. A blank cell means that count is outside the verified bundled subset; the model weight remains available from the full graph. Weights in mV increment synaptic drive rather than immediately changing membrane voltage. Transmitter annotations do not override model signs.
- **Add selected** puts an external neuron on the canvas, up to 100 displayed neurons. It may have only an annotation anchor and no bundled arbor. **Reset circuit view** restores the 43-neuron preset and MN9 selection without resetting the brain.
- Close the observer to stop its worker and refresh timer. Reopen to retain selection, view mode, projection, displayed membership and camera. **Save Workspace** preserves these settings and reopens an observer that was open at save time.

All 579 internal directed edges and 9,272 boundary edges remain active in the canonical graph. The original single-neuron inspection is also available. Circuit asset failures are explicit and do not stop the whole-brain experiment. Missing/corrupt optional skeleton assets leave the verified connection inspector usable.

## Identity and provenance

The exact root IDs, complete annotation rows, per-alias supplement sheet/row references, and exclusions are in [the circuit manifest](circuit/circuit-manifest.json). Nine pathway groups have explicit membership in the pinned Shiu `sez_neurons.pickle`; the Zorro pair comes from the published supplementary tables. The incident-edge audit records 579 internal, 4,668 incoming-boundary and 4,604 outgoing-boundary edges, with 1,936 distinct outside partners.

The source notebook's right-sugar wording conflicts with the v783 left annotations and supplementary `sugar_l` aliases. The preset retains the explicit 20 source IDs; it does not expand to every LB3 neuron. Original sugar ID `720575940620900446` and historical left MN9 `720575940645521262` are absent; no replacements are guessed. Bract is retained as a family alias because old subtype labels do not pair cleanly with current types. Ambiguous Roundtree naming is excluded. An alias or cell type does not specify a different physiological rule.

Sources:

- Shiu, Sterne et al. (2022), [Taste quality and hunger interactions in a feeding sensorimotor circuit](https://doi.org/10.7554/eLife.79887).
- Shiu et al. (2024), [A Drosophila computational brain model reveals sensorimotor processing](https://doi.org/10.1038/s41586-024-07763-9), supplementary tables and [pinned model repository](https://github.com/philshiu/Drosophila_brain_model/tree/91bdd1e7dcf193f3e7ca5a8933497fcef63b7960).
- [FlyWire annotation release v2.1.0](https://github.com/flyconnectome/flywire_annotations/tree/ebd66db2596fcc39c6950fb54ea3efa00f7fe8a0).
- [Official fafbseg skeleton reader](https://fafbseg-py.readthedocs.io/en/latest/_modules/fafbseg/flywire/skeletonize.html), documenting the public v783 endpoint.

Source hashes and individual skeleton checksums are retained under `tools/flybrain/circuit/`. The optional skeleton ZIP contains the 43 unchanged native source binaries; the circuit ZIP contains bulk annotation rows for the preset and its immediate partners, verified contact counts and a compact whole-brain anchor array. Anchors are converted from 4×4×40 nm voxels to micrometres; skeleton coordinates are converted from nanometres at load time. The model's synapse table has no spatial synapse coordinates.

## Regenerate and verify

With the joined annotation sidecar installed:

```sh
python tools/flybrain/prepare_circuit.py
```

The generator verifies annotation content and each downloaded skeleton hash. `--skeleton-cache /path/to/skeletons` uses already downloaded, individually checked `.bin` files. The application verifies the two bundled ZIP fingerprints and checks all incident relationships/contact magnitudes against canonical graph indices/weights. Retain the packaged assets with the demo; the initial viewer requires no network connection.

Targeted tests with the full graph installed:

```sh
./gradlew test --tests '*FlyBrainTest' --tests '*FlyAnnotationsTest' --tests '*FlyCircuitTest'
```

The full-data gates check all 9,851 incident edges, physical coordinate consistency, corrupt/missing resources, exact all-neuron state and delayed-queue parity at every 1 ms step across five one-second conditions, complete 0.1 ms spike events, graph immutability, and saved view state. Full-data tests explicitly skip when the large graph is absent; a closing validation must report zero skips.

On an actual Windows desktop, `validateFlyCircuit` records interaction timings, heap and Windows process memory, simulation runtimes, screenshots and reopen checks using a fixed 2 GiB maximum heap. Set `FLY_CIRCUIT_CONDITION` to `A` (baseline), `B` (candidate closed), or `C` (candidate open). Default is C, with five measured one-second runs after warmup. Build the baseline application from `63a08a87c0c01b948a8804b2a5d1d5e07a298ecf`, adding only the same snapshot harness and Gradle validation task. Do not run builds or other benchmarks concurrently with these measurements.

```powershell
$env:FLY_CIRCUIT_CONDITION='C'
.\gradlew.bat validateFlyCircuit --console=plain --max-workers=2
```

Results are written under `build/circuit-validation-A`, `-B`, or `-C`. Case C exercises 100 cached selections while running, physical projections, zooming, numeric table sorting, external-partner navigation, bounded expansion/reset, ten close/open cycles, and saving/reopening the actual workspace. Its worker is checked for termination after closing. Timing thresholds and interpretation belong in the accompanying validation report; passing numerical tests alone does not prove desktop responsiveness.

See the [MSI validation report](circuit/validation/README.md) for raw results, screenshots, measured targets and shared-machine limitations.

## Integration limits

No new simulated scalar neurons or replacement dynamics are created. The existing sparse arrays remain authoritative. Incident metadata is read in bulk; incoming references are built on a worker, with a 32-neuron bounded cache for subsequent external incoming scans. Additional selected-neuron annotations have a 128-entry cache. Selected voltage/count snapshots use a short state lock and are displayed at 10 Hz. Anatomy is cached as drawing paths/images rather than hundreds of thousands of Swing objects.

Native editing is coordinated with Issue #4; neuron-type-specific physiology remains separate in #5/#7. Simbrain's scalar integrate-and-fire rule is numerically different from the current exact linear rule and cannot be substituted merely to obtain editable node objects.
