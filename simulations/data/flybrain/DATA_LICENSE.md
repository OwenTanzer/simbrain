# FlyWire data attribution and terms

`flywire-v783.bin.gz` is a re-encoding of the complete v783 connectivity and neuron-ID tables distributed with Philip Shiu and Nico Spiller's Drosophila brain model:

https://github.com/philshiu/Drosophila_brain_model/tree/91bdd1e7dcf193f3e7ca5a8933497fcef63b7960

The conversion sorts edges by source neuron, retains every edge, and scales signed anatomical contact counts by 0.275 to obtain the released model's synaptic weights in mV. See `provenance.json` for checksums and exact counts. No anatomical positions, meshes or physiological measurements are included.

FlyWire's public data release is licensed under **Creative Commons Attribution–NonCommercial 4.0 International (CC BY-NC 4.0)**. The derived asset retains those terms, separately from Simbrain's software license.

- Release and citation guidelines: https://join.flywire.ai/guidelines
- License: https://creativecommons.org/licenses/by-nc/4.0/
- Legal code: https://creativecommons.org/licenses/by-nc/4.0/legalcode

Credit FlyWire, the consortium and the source model authors. Cite:

- Dorkenwald et al. *Neuronal wiring diagram of an adult brain*. Nature 634, 124–138 (2024). https://doi.org/10.1038/s41586-024-07558-y
- Schlegel et al. *Whole-brain annotation and multi-connectome cell typing of Drosophila*. Nature 634, 139–152 (2024). https://doi.org/10.1038/s41586-024-07686-5
- The Shiu et al. whole-brain model and released source linked above.

The data providers have not endorsed this local Simbrain adaptation.

`annotations-v783.tsv.gz` is a row-ordered subset of the v783 neuron annotations
published by the FlyWire Consortium at
https://github.com/flyconnectome/flywire_annotations/tree/v2.1.0 . The conversion
preserves every annotation column for each neuron in the Shiu v783 model. It
excludes annotation rows for neurons absent from that model. These data retain
the FlyWire CC BY-NC 4.0 terms above. The annotation release is pinned to
commit `ebd66db2596fcc39c6950fb54ea3efa00f7fe8a0`; the source and output
checksums and coverage are in `tools/flybrain/annotation-coverage-v783.json`.

Anchor and soma coordinates use FlyWire's 4×4×40 nm voxel space. Anchor
positions are not necessarily somata or a ready-made two-dimensional GUI layout.
Cell types and predicted neurotransmitters are annotations, not measured
cell-specific membrane dynamics.

`feeding-circuit.zip` is a selected re-encoding of the pinned v783 annotations,
contact table and anatomical anchor coordinates. `feeding-skeletons.zip` retains
43 native skeleton binaries from the public FlyWire v783 skeleton endpoint
served by FlyConnectome at the MRC Laboratory of Molecular Biology. These data
retain their source attribution and FlyWire terms, separately from the software.
Source URLs, exact IDs, transformations and checksums are recorded in
`tools/flybrain/CIRCUIT.md` and `tools/flybrain/circuit/`. Cite the FlyWire papers
above, Shiu et al. (2024), DOI 10.1038/s41586-024-07763-9, and Shiu, Sterne et al.
(2022), DOI 10.7554/eLife.79887. Circuit membership is a documented display subset;
no claim is made that it constitutes a complete or isolated feeding circuit.
