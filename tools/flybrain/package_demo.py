"""Package a built Java distribution, full corresponding source, local data and validation evidence."""
import argparse
from pathlib import Path
import shutil
import subprocess
import zipfile

BASELINE = "3a5463e0f686b397a0176f4967171178a5af45ff"
MAIN = "org.simbrain.custom_sims.simulations.neuroscience.FlyBrainLauncherKt"
OPENS = " ".join("--add-opens " + package + "=ALL-UNNAMED" for package in [
    "java.base/java.util", "java.desktop/java.awt", "java.desktop/java.awt.geom",
    "java.base/java.util.concurrent", "java.base/java.util.concurrent.atomic", "java.base/java.lang",
])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    root = Path.cwd()
    stage = args.output.parent / "FlyBrain-Simbrain-Local-Demo"
    stage.mkdir(parents=True, exist_ok=True)
    distributions = list((root / "build/install").glob("*/lib"))
    assert len(distributions) == 1, "Run Gradle installDist -PflyBrainDemo first."
    shutil.copytree(distributions[0], stage / "app/lib", dirs_exist_ok=True)
    shutil.copytree(root / "simulations/data/flybrain", stage / "simulations/data/flybrain", dirs_exist_ok=True)
    shutil.copytree(root / "build/flybrain-demo", stage / "example-experiment", dirs_exist_ok=True)
    shutil.copy2(root / "tools/flybrain/README.md", stage / "README.md")
    (stage / "licenses").mkdir(exist_ok=True)
    shutil.copy2(root / "LICENSE", stage / "licenses/SIMBRAIN-GPL.txt")
    shutil.copy2(root / "tools/flybrain/SHIU_LICENSE.txt", stage / "licenses/SHIU-MIT.txt")
    unix = f'''#!/usr/bin/env bash
set -e
cd -- "$(dirname -- "${{BASH_SOURCE[0]}}")"
exec java -Xmx2g {OPENS} -cp "app/lib/*" {MAIN} "$@"
'''
    for name in ["start-flybrain.sh", "start-flybrain.command"]:
        p = stage / name
        p.write_text(unix)
        p.chmod(0o755)
    (stage / "start-flybrain.bat").write_text(f'@echo off\ncd /d "%~dp0"\njava -Xmx2g {OPENS} -cp "app/lib/*" {MAIN} %*\nif errorlevel 1 pause\n')
    paths = subprocess.check_output(["git", "ls-files", "-z"]).decode().split("\0")
    with zipfile.ZipFile(stage / "source.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for name in paths:
            if name and (root / name).is_file():
                archive.write(root / name, "simbrain-source/" + name)
    patch = subprocess.check_output(["git", "diff", BASELINE, "--", "."])
    (stage / "review.patch").write_bytes(patch)
    for name in ["VALIDATION.md", "flybrain-desktop.png"]:
        shutil.copy2(args.output.parent / name, stage / name)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(stage.rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(stage.parent))
    with zipfile.ZipFile(args.output) as archive:
        assert archive.testzip() is None
    print(f"Packaged {args.output} ({args.output.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
