"""Reproduce the bounded feeding viewer assets from the pinned research audit and annotations."""
import argparse
import csv
import gzip
import hashlib
import io
import json
import struct
import urllib.request
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'tools/flybrain/circuit'
DEST = ROOT / 'simulations/data/flybrain'


def archive(path, entries):
    with zipfile.ZipFile(path, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
        for name, content in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            out.writestr(info, content)
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--skeleton-cache', type=Path)
    args = p.parse_args()
    manifest = json.loads((SOURCE / 'circuit-manifest.json').read_text())
    provenance = json.loads((SOURCE / 'source-provenance.json').read_text())
    audit = json.loads(gzip.decompress((SOURCE / 'connectivity-audit.json.gz').read_bytes()))
    content = gzip.decompress((DEST / 'annotations-v783.tsv.gz').read_bytes())
    assert hashlib.sha256(content).hexdigest() == provenance['joined_annotation_decompressed_sha256']
    selected = {n['root_id']: n for n in manifest['nodes']}
    incident_ids = {x for e in audit['edges'] for x in e[:2]}
    reader = csv.DictReader(io.StringIO(content.decode()), delimiter='\t')
    header = reader.fieldnames + ['alias_group', 'in_preset']
    output = io.StringIO(newline='')
    writer = csv.DictWriter(output, fieldnames=header, delimiter='\t', lineterminator='\n')
    writer.writeheader()
    context = bytearray(struct.pack('>i', 138639))
    found = set()
    for row in reader:
        context.extend(struct.pack('>fff', *(float(row['pos_' + a]) * s for a,s in zip('xyz', [.004,.004,.04]))))
        root = row['root_id']
        if root in incident_ids:
            found.add(root)
            row['alias_group'] = selected.get(root, {}).get('alias_group', '')
            row['in_preset'] = '1' if root in selected else '0'
            writer.writerow(row)
    assert found == incident_ids and len(context) == 4 + 138639 * 12
    edges = 'source_id\ttarget_id\tcontacts\n' + ''.join(f'{e[0]}\t{e[1]}\t{e[4]}\n' for e in audit['edges'])
    digest = archive(DEST / 'feeding-circuit.zip', {
        'neurons.tsv':output.getvalue().encode(), 'edges.tsv':edges.encode(), 'context.bin':context,
        'manifest.json':(SOURCE / 'circuit-manifest.json').read_bytes(),
        'provenance.json':(SOURCE / 'source-provenance.json').read_bytes(),
    })
    skeletons = {}
    for item in json.loads((SOURCE / 'morphology-audit.json').read_text()):
        local = args.skeleton_cache / (item['root_id'] + '.bin') if args.skeleton_cache else None
        data = local.read_bytes() if local and local.exists() else urllib.request.urlopen(item['url'], timeout=30).read()
        assert hashlib.sha256(data).hexdigest() == item['sha256']
        skeletons[item['root_id']+'.bin'] = data
    morphology = archive(DEST / 'feeding-skeletons.zip', skeletons)
    print(json.dumps({'feeding-circuit.zip':digest, 'feeding-skeletons.zip':morphology}, indent=2))


if __name__ == '__main__':
    main()
