"""Builds function.zip for the web-extract Lambda (Python 3.12, x86_64).

Run from anywhere:  python mcp/web-extract/build.py
The dependencies are downloaded as Linux wheels, so the zip works on Lambda even when built on Windows or macOS.
"""
import os
import shutil
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(HERE, "build")
ZIP = os.path.join(HERE, "function.zip")

shutil.rmtree(BUILD, ignore_errors=True)
subprocess.run([
    sys.executable, "-m", "pip", "install", "--quiet", "--target", BUILD,
    "--platform", "manylinux2014_x86_64", "--python-version", "3.12", "--implementation", "cp",
    "--only-binary=:all:", "-r", os.path.join(HERE, "requirements.txt"),
], check=True)
shutil.copy(os.path.join(HERE, "lambda_function.py"), BUILD)

# Trim what Lambda never loads.
for root, dirs, files in os.walk(BUILD, topdown=False):
    for d in dirs:
        if d in ("__pycache__", "tests", "test") or d.endswith(".dist-info"):
            shutil.rmtree(os.path.join(root, d), ignore_errors=True)

with zipfile.ZipFile(ZIP, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    for root, _, files in os.walk(BUILD):
        for name in files:
            path = os.path.join(root, name)
            z.write(path, os.path.relpath(path, BUILD))
shutil.rmtree(BUILD, ignore_errors=True)
print(f"Built {ZIP} ({os.path.getsize(ZIP) / 1024 / 1024:.1f} MB)")
