"""Regression for review #3: run real Java, then inject incomplete/invalid output.

Run from the repository root:
python docs/verify_comparison_guard.py . /path/to/jdk-21/bin/java
Uses only the standard library; builds must have been completed beforehand.
"""
import contextlib
import io
import json
from pathlib import Path
import runpy
import subprocess
import sys
import tempfile
from unittest.mock import patch


def main():
    root = Path(sys.argv[1] if len(sys.argv) > 1 else Path(__file__).resolve().parents[1]).resolve()
    java = sys.argv[2]
    real_run = subprocess.run
    failures = []
    variants = ("complete", "missing", "empty", "extra", "object")
    for variant in variants:
        with tempfile.TemporaryDirectory(prefix="warehouse-parity-guard-") as directory:
            observed = {}

            def inject(command, **kwargs):
                result = real_run(command, **kwargs)
                output = Path(command[-1])
                values = json.loads(output.read_text(encoding="utf-8"))
                observed["expected"] = len(values)
                if variant == "missing":
                    values = values[:-1]
                elif variant == "empty":
                    values = []
                elif variant == "extra":
                    values.append(values[-1])
                elif variant == "object":
                    values = {}
                observed["actual"] = len(values)
                output.write_text(json.dumps(values, ensure_ascii=False), encoding="utf-8")
                return result

            captured = io.StringIO()
            code = 0
            with patch.object(sys, "argv", ["compare_versions.py", str(root), java, directory]), \
                    patch("subprocess.run", side_effect=inject), \
                    contextlib.redirect_stdout(captured), contextlib.redirect_stderr(captured):
                try:
                    runpy.run_path(str(root / "docs/compare_versions.py"), run_name="__main__")
                except SystemExit as error:
                    code = error.code if isinstance(error.code, int) else 1
                    if not isinstance(error.code, int):
                        print(error.code)
            output = captured.getvalue()
            expected = observed["expected"]
            if variant == "complete":
                passed = code == 0 and f"{expected}/{expected}" in output
            else:
                passed = code != 0 and "совпали" not in output
                if variant != "object":
                    passed = passed and str(expected) in output and str(observed["actual"]) in output
            print(f"{'PASS' if passed else 'FAIL'} {variant}: expected={expected}, Java={observed['actual']}, exit={code}")
            if not passed:
                failures.append(variant)
                print(output)
    if failures:
        raise SystemExit("Comparison guard failed: " + ", ".join(failures))


if __name__ == "__main__":
    main()
