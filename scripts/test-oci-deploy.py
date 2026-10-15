#!/usr/bin/env python3
"""Compatibility command for the shared cloud deployment security regressions."""
import runpy
from pathlib import Path

if __name__ == "__main__":
    runpy.run_path(str(Path(__file__).with_name("test-cloud-deploy.py")), run_name="__main__")
