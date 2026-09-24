# Fly brain in Simbrain — local demo

A whole-connectome, leaky integrate-and-fire simulation using FlyWire v783 and the released Shiu et al. model tables. This is a local contribution candidate, not an official Simbrain release.

## Run the packaged demo

Install a desktop Java 17 or newer runtime. Unzip the complete demo folder, then:

- **macOS / Linux:** run `bash start-flybrain.sh` from a terminal. On macOS you can also try `start-flybrain.command`.
- **Windows:** double-click `start-flybrain.bat`.

The launcher sets the working directory automatically, so the wiring data can be found. Java is the only runtime requirement; Python and Brian2 are not required for the packaged application. The bundle includes native dependencies for Linux, macOS and Windows; execution was validated here on Linux with Java 17. A 2 GB Java heap is configured; allow additional memory for the desktop and operating system.

Click **Run 1 s** in the experiment controls. Watch the whole-brain spike plot and MN9 voltage trace. Toggle **Stimulus: ON** to turn off input, reset, then run again for a baseline. **Sugar preset** restores the default experiment. Enter other FlyWire IDs to stimulate, inspect or silence other neurons. **Apply + reset** validates all settings before changing anything.

The array image shows membrane potential in dataset order, not anatomical positions. The wiring is real connectome data; the positions in this image are only an index layout. There is no virtual body or training task in this first demo.

**File → Save Workspace** preserves state, pending delayed events, input settings and the pseudorandom stream. Keep the `simulations/data/flybrain` directory with the application. Exported spike times have 0.1 ms resolution; the live plots sample each 1 ms. Recorded events are capped at 200,000 per reset, and the export reports any omitted events. Counts for every neuron remain complete.

## Data and experiment

- 138,639 neurons, 15,091,983 directed weighted connections, representing 54,492,922 anatomical synaptic contacts.
- Full connectivity is retained. No pruning, dense weight matrix or substitution of a small circuit.
- Default: 100 Hz independent input to 20 sensory neurons retained from the released sugar example, seed 42; watch MN9 (`720575940660219265`).
- The original example uses v630 and 21 sensory IDs. ID `720575940620900446` is absent from the v783 table and is explicitly omitted here. No replacement ID is guessed. This is an exploratory v783 preset, not a claim to reproduce the paper's behavioral figures.
- Silencing blocks outgoing transmission, matching the released model's implementation. It does not prevent the silenced neuron from receiving input or firing.
- Changing the stimulus toggle mid-run is allowed. Exported settings describe the current configuration; they are not an intervention history. For controlled comparisons, reset between conditions.

## Model mapping

| Source behavior | Implementation |
| --- | --- |
| LIF membrane and synaptic current | Exact linear integration, 0.1 ms internal step |
| Rest/reset and threshold | −52 mV; strict threshold > −45 mV |
| Time constants | Membrane 20 ms; synaptic decay 5 ms |
| Refractory period | 2.2 ms, zero for stimulated neurons |
| Transmission | 1.8 ms delayed source events, signed contact count × 0.275 mV |
| External Poisson input | 68.75 mV jumps; Bernoulli sampling at 0.1 ms with a local seeded generator |
| Update order | Integrate → threshold → synapses/input → reset |
| Simbrain network step | 1 ms, containing ten internal steps |
| Native array activations | Membrane voltage; array input adds mV once per network step |
| Native spike flags | True if that neuron fired at least once within the network step |

This is a model constrained by a wiring map. Synaptic counts, transmitter-derived signs and generic neuron dynamics do not reconstruct all the fly's physiology, memories or behavior. There is no plasticity rule enabled in this demo.

## Source build and tests

The bundle includes the complete corresponding Simbrain source and a small review patch. The baseline is Simbrain commit `3a5463e0f686b397a0176f4967171178a5af45ff`. Extract `source.zip`, copy the bundled data directory into the extracted source's `simulations/data/flybrain`, then run from the source root:

```sh
bash gradlew run -PflyBrainDemo
bash gradlew test --tests 'org.simbrain.custom_sims.simulations.neuroscience.FlyBrainTest'
bash gradlew runSim -PsimName='Fly brain (FlyWire v783)' -PoptionString=benchmark:1000
bash gradlew uiSnapshot -PsnapshotDef=org.simbrain.util.uisnapshot.FlyBrainSnapshot
```

A source build needs JDK 17 and downloads Gradle dependencies. In a standard Simbrain build, the simulation is in **Simulations → Neuroscience → Fly brain (FlyWire v783)**. The `-PflyBrainDemo` build property only selects the direct demo launcher.

The full-data persistence test runs when the asset is present; the small numerical tests need no external dataset. `VALIDATION.md` in the demo bundle records the actual checks and benchmark from this build.

To regenerate the data asset:

```sh
python -m pip install numpy pandas pyarrow
python tools/flybrain/prepare_data.py
```

To create the v783 neuron-annotation sidecar and its coverage report:

```sh
python tools/flybrain/prepare_annotations.py
```

This Python standard-library converter downloads checksummed input tables from
the pinned Shiu revision and FlyWire annotation release v2.1.0. It writes
`simulations/data/flybrain/annotations-v783.tsv.gz` in exactly the model's
neuron order, with all annotation columns retained, and
`tools/flybrain/annotation-coverage-v783.json` with field coverage and output
content checksum. The sidecar is separate from the simulation and does not change its
firing dynamics. In the simulation, set a **Watch ID** and click **Apply + reset**, then
**Inspect watch neuron** to see its annotation and strongest outgoing connections.
If the sidecar has not been generated, the wiring remains available in that dialog;
a changed or corrupt sidecar produces a warning. `root_id` is the join key; `pos_x/y/z` are anchor
coordinates and `soma_x/y/z` are soma coordinates in 4×4×40 nm voxel space.
The reader verifies the hash of the decompressed TSV; gzip bytes may differ
between compression libraries even when the annotation content is identical.
Missing class/type labels should remain missing rather than be inferred from
neighboring neurons. The annotation release is pinned because later revisions
may alter labels even when the connectome materialization stays v783.

The converter downloads pinned source tables and verifies both SHA-256 checksums before conversion. `provenance.json` records the output hash and binary schema. `reference_fixture.py` regenerates the small independent Brian2 oracle from the pinned source checkout; see its command-line usage. The ordinary JVM tests consume the recorded fixture, without requiring Brian2.

## Contribution boundaries

The implementation adds a custom `FlyBrainRule`, state holder, sparse asset loader, simulation and launcher. Simbrain supplies the native `NeuronArray`, scheduler, time-series plots, controls, couplings and workspace serialization. There are no changes to the neural update engine. A small shared serialization addition compactly encodes large integer, boolean and label arrays, extending the existing compact double-array approach; it retains legacy XML support. A native array deserialization hook also restores the saved state holder after the update-rule setter runs, preventing that setter from resetting saved dynamics. Array image colors now use the rule's graphical bounds so millivolt-scale voltages remain visible, and the image cache preserves transparent spike overlays. One menu registration and an optional build launcher select the new simulation.

Before upstream inclusion, Jeff can decide naming, preferred UI layout, whether the 51 MB data asset should be downloaded on demand, and whether to add other presets or annotation resources. A small-circuit browser and virtual body are possible follow-up features, not requirements for running this demo.

## Credits and terms

- [Simbrain](https://github.com/simbrain/simbrain), Jeff Yoshimi and contributors; see the included GPL license and corresponding source.
- [Shiu et al., Drosophila brain model](https://github.com/philshiu/Drosophila_brain_model), pinned revision `91bdd1e7dcf193f3e7ca5a8933497fcef63b7960`; model source is MIT licensed, with the original notice included.
- [FlyWire citation and release guidelines](https://join.flywire.ai/guidelines): public data are CC BY-NC 4.0. The derived connectivity asset retains those terms separately from the application code.
- Dorkenwald et al., *Neuronal wiring diagram of an adult brain*, Nature (2024), https://doi.org/10.1038/s41586-024-07558-y.
- Schlegel et al., *Whole-brain annotation and multi-connectome cell typing of Drosophila*, Nature (2024), https://doi.org/10.1038/s41586-024-07686-5.

This build has not been submitted to Jeff or published as a pull request.
