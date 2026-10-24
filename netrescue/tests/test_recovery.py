"""Tests for the RecoveryEngine workflow."""

import pytest

from netrescue.core.graph import Graph
from netrescue.core.generator import ring
from netrescue.core.failure import FailureSimulator
from netrescue.core.recovery import recover

def test_recover_no_failure():
    g = ring(5)
    src, dst = "R0", "R3"
    sim = FailureSimulator()
    result = recover(g, src, dst, sim)
    assert result["status"] == "UNAFFECTED"
    assert result["old_path"] == result["new_path"]
    assert result["affected"] is False

def test_recover_node_failure_on_path():
    g = ring(5)
    src, dst = "R0", "R3"
    sim = FailureSimulator()
    sim.fail_node("R4")  # R4 lies on the selected shortest path R0-R4-R3
    result = recover(g, src, dst, sim)
    assert result["status"] in {"RECOVERED", "DEGRADED"}
    assert result["affected"] is True
    # New path should avoid R2
    assert "R2" not in result["new_path"]

def test_recover_disconnected():
    g = ring(4)
    src, dst = "R0", "R2"
    sim = FailureSimulator()
    # Fail two opposite edges to break the ring into two components
    sim.fail_edge("R0", "R1")
    sim.fail_edge("R2", "R3")
    result = recover(g, src, dst, sim)
    assert result["status"] == "DISCONNECTED"
    assert result["new_path"] == []
    assert result["components"] is not None
    # Should have at least two components
    assert len(result["components"]) >= 2
