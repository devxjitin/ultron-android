"""Build the read-only YouTube Lambda for Python 3.14 on Linux x86_64."""
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

HERE = Path(__file__).resolve().parent
BUILD = HERE / "build"


def main():
    # Both paths are fixed children of this build script's directory.
    if BUILD.parent != HERE:
        raise RuntimeError("Invalid build path")
    shutil.rmtree(BUILD, ignore_errors=True)
    subprocess.run([sys.executable, "-m", "pip", "install", "--quiet", "--target", str(BUILD),
                    "--platform", "manylinux2014_x86_64", "--python-version", "3.14", "--implementation", "cp",
                    "--only-binary=:all:", "-r", str(HERE / "requirements.txt")], check=True)
    shutil.copy(HERE / "lambda_function.py", BUILD)
    shutil.copy(HERE.parent / "web-extract" / "appcheck.py", BUILD)
    with zipfile.ZipFile(HERE / "function.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in BUILD.rglob("*"):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, path.relative_to(BUILD))
    shutil.rmtree(BUILD)
    print("Built", HERE / "function.zip")


if __name__ == "__main__":
    main()
