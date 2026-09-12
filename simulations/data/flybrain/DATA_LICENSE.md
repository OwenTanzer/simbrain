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
