#!/usr/bin/env python3
"""Validate a private synthetic payload independently of production operations."""
import argparse
import json
from pathlib import Path
from generate_demo_data import validate_dataset


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", type=Path, required=True)
    parser.add_argument("--audit-before", type=Path, required=True)
    args = parser.parse_args()
    result = validate_dataset(json.loads(args.dataset.read_text()), json.loads(args.audit_before.read_text()))
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
