#!/usr/bin/env python3
"""
Compare the latest adaptive and random run JSON files in the repository results/ folder.
Writes a comparison JSON and CSV to results/comparison-<timestamp>.json/.csv

Usage:
  python tools/compare_results.py

The script looks for files named run-*.json and reads their "mode" and "metrics" fields.
If metrics is missing it will compute a few basic ones from the players list.
"""
import json
import os
from datetime import datetime, timezone
from glob import glob

RESULTS_DIR = os.path.join(os.path.dirname(__file__), '..', 'results')
if not os.path.isabs(RESULTS_DIR):
    RESULTS_DIR = os.path.abspath(RESULTS_DIR)

def load_runs():
    pattern = os.path.join(RESULTS_DIR, 'run-*.json')
    paths = glob(pattern)
    runs = []
    for p in paths:
        try:
            with open(p, 'r', encoding='utf-8') as f:
                text = f.read()
            try:
                data = json.loads(text)
            except json.JSONDecodeError as e:
                # Attempt a simple repair: convert comma decimals like 0,1564 -> 0.1564
                import re
                repaired = re.sub(r'(:\s*)(-?\d+),(\d+)', r'\1\2.\3', text)
                try:
                    data = json.loads(repaired)
                    print(f'Repaired and loaded {p} (original JSON error: {e})')
                except Exception:
                    raise e
            data['_path'] = p
            runs.append(data)
        except Exception as e:
            print(f'Warning: failed to load {p}: {e}')
    return runs

def parse_ts(ts):
    # normalize Z to +00:00
    if ts is None:
        return None
    s = ts
    if s.endswith('Z'):
        s = s[:-1] + '+00:00'
    try:
        return datetime.fromisoformat(s)
    except Exception:
        try:
            # fallback: try a few formats
            return datetime.strptime(s, '%Y-%m-%dT%H:%M:%S.%f%z')
        except Exception:
            return None


def metrics_from_run(run):
    # prefer metrics block if present
    if 'metrics' in run and isinstance(run['metrics'], dict) and run['metrics']:
        return run['metrics']
    # fallback: compute simple metrics from players list
    players = run.get('players', [])
    total = len(players)
    alive_count = sum(1 for p in players if p.get('alive'))
    avg_kills = sum(p.get('kills', 0) for p in players) / total if total else 0
    kills = [p.get('kills', 0) for p in players]
    mean_k = avg_kills
    var_k = sum((k - mean_k) ** 2 for k in kills) / total if total else 0
    avg_activity = sum(p.get('activity', 0) for p in players) / total if total else 0
    # avgDistanceToCenter isn't recorded reliably in the run JSON without zone center; use distance field if present
    distances = [p.get('distance', None) for p in players if p.get('distance') is not None]
    avg_dist = sum(distances) / len(distances) if distances else 0
    winner_id = run.get('winnerLeftId')
    winner_kills = 0
    for p in players:
        if p.get('id') == winner_id:
            winner_kills = p.get('kills', 0)
            break
    rounds = run.get('rounds', 0)
    eliminations_per_round = (total - alive_count) / rounds if rounds else 0
    return {
        'avgDistanceToCenter': avg_dist,
        'aliveCount': alive_count,
        'avgKills': avg_kills,
        'aliveRatio': (alive_count / total) if total else 0,
        'killVariance': var_k,
        'avgActivity': avg_activity,
        'winnerKills': winner_kills,
        'roundsPlayed': rounds,
        'eliminationsPerRound': eliminations_per_round
    }


def pick_latest_by_mode(runs):
    by_mode = {}
    for r in runs:
        mode = r.get('mode', 'adaptive')
        ts = parse_ts(r.get('timestamp'))
        if ts is None:
            # try to derive from filename
            fname = os.path.basename(r.get('_path',''))
            # strip run- prefix and file ext
            # fallback: use file mtime
            ts = datetime.fromtimestamp(os.path.getmtime(r.get('_path')), tz=timezone.utc)
        current = by_mode.get(mode)
        if current is None or ts > current[0]:
            by_mode[mode] = (ts, r)
    # return dict mode -> run
    return {m: info[1] for m, info in by_mode.items()}


def compare_metrics(adaptive_metrics, random_metrics):
    keys = set(adaptive_metrics.keys()) | set(random_metrics.keys())
    numeric = {}
    diffs = {}
    for k in keys:
        a = adaptive_metrics.get(k)
        b = random_metrics.get(k)
        try:
            a_f = float(a)
        except Exception:
            a_f = None
        try:
            b_f = float(b)
        except Exception:
            b_f = None
        numeric[k] = { 'adaptive': a_f, 'random': b_f }
        if a_f is not None and b_f is not None:
            diffs[k] = b_f - a_f
        else:
            diffs[k] = None
    # compute some higher-level derived diffs as suggested
    derived = {
        'fairnessImprovement': (adaptive_metrics.get('avgDistanceToCenter', 0) - random_metrics.get('avgDistanceToCenter', 0)) if adaptive_metrics and random_metrics else None,
        'survivabilityGain': (adaptive_metrics.get('aliveRatio', 0) - random_metrics.get('aliveRatio', 0)) if adaptive_metrics and random_metrics else None,
        'stabilityGain': (adaptive_metrics.get('killVariance', 0) - random_metrics.get('killVariance', 0)) if adaptive_metrics and random_metrics else None
    }
    return { 'perMetric': numeric, 'diffs': diffs, 'derived': derived }


def write_output(comp, adaptive_run, random_run):
    now = datetime.now(timezone.utc).isoformat().replace(':','-')
    out_json = os.path.join(RESULTS_DIR, f'comparison-{now}.json')
    out_csv = os.path.join(RESULTS_DIR, f'comparison-{now}.csv')
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump({ 'comparison': comp, 'adaptive_run': adaptive_run.get('_path'), 'random_run': random_run.get('_path') }, f, indent=2)
    # write CSV: rows = metric, adaptive, random, diff
    perMetric = comp['perMetric']
    with open(out_csv, 'w', encoding='utf-8') as f:
        f.write('metric,adaptive,random,diff\n')
        for k, v in perMetric.items():
            a = v.get('adaptive')
            b = v.get('random')
            d = comp['diffs'].get(k)
            f.write(f'{k},{a},{b},{d}\n')
    print(f'Wrote comparison JSON: {out_json}')
    print(f'Wrote comparison CSV: {out_csv}')
    # generate visualizations (bar chart for metrics + history line charts)
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt

        # bar chart for selected metrics
        metrics_to_plot = ['avgDistanceToCenter', 'aliveRatio', 'avgKills', 'killVariance', 'winnerKills', 'eliminationsPerRound']
        adaptive_vals = []
        random_vals = []
        labels = []
        for m in metrics_to_plot:
            labels.append(m)
            a = perMetric.get(m, {}).get('adaptive')
            b = perMetric.get(m, {}).get('random')
            # convert None to 0 for plotting
            adaptive_vals.append(0.0 if a is None else float(a))
            random_vals.append(0.0 if b is None else float(b))

        x = range(len(labels))
        width = 0.35
        fig, ax = plt.subplots(figsize=(10,5))
        ax.bar([i - width/2 for i in x], adaptive_vals, width, label='adaptive')
        ax.bar([i + width/2 for i in x], random_vals, width, label='random')
        ax.set_ylabel('Value')
        ax.set_title('Adaptive vs Random — selected metrics')
        ax.set_xticks(list(x))
        ax.set_xticklabels(labels, rotation=25, ha='right')
        ax.legend()
        plt.tight_layout()
        out_metrics_png = os.path.join(RESULTS_DIR, f'comparison-{now}-metrics.png')
        fig.savefig(out_metrics_png)
        plt.close(fig)
        print(f'Wrote metrics chart: {out_metrics_png}')

        # (histories plotting removed) only metrics bar chart is generated
    except Exception as e:
        print('Matplotlib not available or plotting failed:', e)
        print('To enable charts, install matplotlib: python -m pip install matplotlib')


def main():
    runs = load_runs()
    if not runs:
        print('No runs found in results/. Run some simulations first.')
        return
    by_mode = pick_latest_by_mode(runs)
    adaptive = by_mode.get('adaptive')
    random = by_mode.get('random')
    if adaptive is None or random is None:
        print('Need at least one adaptive and one random run to compare. Found modes:', list(by_mode.keys()))
        return
    am = metrics_from_run(adaptive)
    rm = metrics_from_run(random)
    comp = compare_metrics(am, rm)
    write_output(comp, adaptive, random)

if __name__ == '__main__':
    main()
