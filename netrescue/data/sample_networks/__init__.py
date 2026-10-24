import os
import json
from pathlib import Path

BASE = Path(__file__).resolve().parents[2] / "data" / "sample_networks"
BASE.mkdir(parents=True, exist_ok=True)

sample = {
    "R0": {"R1": 5, "R2": 3},
    "R1": {"R0": 5, "R3": 2},
    "R2": {"R0": 3, "R3": 4},
    "R3": {"R1": 2, "R2": 4}
}

with open(BASE / "small_office.json", "w", encoding="utf-8") as f:
    json.dump(sample, f, indent=2)
