"""Engine sound metadata shared with the old generator (tools/vehgen/sounds.py)."""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'vehgen'))
import sounds


def engine_meta(t):
    E = sounds.ENGINES[t]
    return dict(type=t, idle=E['idle'], drive=E['drive'], cyl=E['cyl'], diesel=E['clatter'] > 0)
