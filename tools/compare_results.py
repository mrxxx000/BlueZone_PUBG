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
import sys
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
        # return a shallow copy with aliveRatio removed so downstream code doesn't see it
        m = dict(run['metrics'])
        m.pop('aliveRatio', None)
        return m
    # fallback: compute simple metrics from players list
    players = run.get('players', [])
    total = len(players)
    alive_count = sum(1 for p in players if p.get('alive'))
    avg_kills = sum(p.get('kills', 0) for p in players) / total if total else 0
    kills = [p.get('kills', 0) for p in players]
    mean_k = avg_kills
    var_k = sum((k - mean_k) ** 2 for k in kills) / total if total else 0
    avg_activity = sum(p.get('activity', 0) for p in players) / total if total else 0
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
            ts = datetime.fromtimestamp(os.path.getmtime(r.get('_path')), tz=timezone.utc)
        current = by_mode.get(mode)
        if current is None or ts > current[0]:
            by_mode[mode] = (ts, r)
    return {m: info[1] for m, info in by_mode.items()}


def pick_latest_n_by_mode(runs, n=25):
    """Return dict mode -> list of up to n runs sorted by timestamp desc."""
    from collections import defaultdict
    grouped = defaultdict(list)
    for r in runs:
        mode = r.get('mode', 'adaptive')
        ts = parse_ts(r.get('timestamp'))
        if ts is None:
            ts = datetime.fromtimestamp(os.path.getmtime(r.get('_path')), tz=timezone.utc)
        grouped[mode].append((ts, r))
    out = {}
    for mode, arr in grouped.items():
        arr.sort(key=lambda x: x[0], reverse=True)
        out[mode] = [item[1] for item in arr[:n]]
    return out


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
    derived = {
        'fairnessImprovement': (adaptive_metrics.get('avgDistanceToCenter', 0) - random_metrics.get('avgDistanceToCenter', 0)) if adaptive_metrics and random_metrics else None,
        'stabilityGain': (adaptive_metrics.get('killVariance', 0) - random_metrics.get('killVariance', 0)) if adaptive_metrics and random_metrics else None
    }
    return { 'perMetric': numeric, 'diffs': diffs, 'derived': derived }


def aggregate_runs_metrics(runs_list):
    """Given a list of run dicts, compute aggregated stats (mean, std, count) per metric.
    Returns dict metric -> {values: [...], mean:, std:, count:, missing:...}
    """
    import statistics
    per_metric_vals = {}
    for run in runs_list:
        m = metrics_from_run(run)
        for k, v in m.items():
            try:
                val = float(v)
            except Exception:
                val = None
            per_metric_vals.setdefault(k, []).append(val)
    aggregated = {}
    for k, vals in per_metric_vals.items():
        nums = [v for v in vals if v is not None]
        cnt = len(nums)
        total = len(vals)
        mean_v = statistics.mean(nums) if cnt > 0 else None
        try:
            std_v = statistics.pstdev(nums) if cnt > 0 else None
        except Exception:
            std_v = None
        aggregated[k] = {
            'values': vals,
            'mean': mean_v,
            'std': std_v,
            'count': cnt,
            'total': total,
            'missing': total - cnt
        }
    return aggregated


def write_output(comp, adaptive_run, random_run):
    now = datetime.now(timezone.utc).isoformat().replace(':','-')
    out_json = os.path.join(RESULTS_DIR, f'comparison-{now}.json')
    out_csv = os.path.join(RESULTS_DIR, f'comparison-{now}.csv')
    # comp is expected to be a dict with 'perMetric' mapping metric -> {adaptive: {mean,std,count,total}, random: {...}, diff: ...}
    with open(out_json, 'w', encoding='utf-8') as f:
        json.dump({ 'comparison': comp, 'adaptive_runs': [r.get('_path') for r in adaptive_run], 'random_runs': [r.get('_path') for r in random_run] }, f, indent=2)
    # write CSV: rows = metric, adaptive_mean,adaptive_std,adaptive_count,random_mean,random_std,random_count,diff_mean
    perMetric = comp['perMetric']
    with open(out_csv, 'w', encoding='utf-8') as f:
        f.write('metric,adaptive_mean,adaptive_std,adaptive_count,adaptive_total,random_mean,random_std,random_count,random_total,diff_mean\n')
        for k, v in perMetric.items():
            a = v.get('adaptive', {})
            b = v.get('random', {})
            d = v.get('diff')
            f.write(f"{k},{a.get('mean')},{a.get('std')},{a.get('count')},{a.get('total')},{b.get('mean')},{b.get('std')},{b.get('count')},{b.get('total')},{d}\n")
    print(f'Wrote comparison JSON: {out_json}')
    print(f'Wrote comparison CSV: {out_csv}')
    # generate visualizations (bar chart for metrics + history line charts)
    try:
        import matplotlib
        matplotlib.use('Agg')
        import matplotlib.pyplot as plt

        # bar chart for selected metrics (plot means, with std error bars if available)
        metrics_to_plot = ['avgDistanceToCenter', 'avgKills', 'killVariance', 'winnerKills', 'eliminationsPerRound']
        adaptive_means = []
        random_means = []
        adaptive_err = []
        random_err = []
        labels = []
        for m in metrics_to_plot:
            labels.append(m)
            a = perMetric.get(m, {}).get('adaptive', {})
            b = perMetric.get(m, {}).get('random', {})
            mean_a = 0.0 if a.get('mean') is None else float(a.get('mean'))
            mean_b = 0.0 if b.get('mean') is None else float(b.get('mean'))
            std_a = 0.0 if a.get('std') is None else float(a.get('std'))
            std_b = 0.0 if b.get('std') is None else float(b.get('std'))
            adaptive_means.append(mean_a)
            random_means.append(mean_b)
            adaptive_err.append(std_a)
            random_err.append(std_b)

        x = range(len(labels))
        width = 0.35
        fig, ax = plt.subplots(figsize=(10,5))
        ax.bar([i - width/2 for i in x], adaptive_means, width, yerr=adaptive_err, label='adaptive', capsize=4)
        ax.bar([i + width/2 for i in x], random_means, width, yerr=random_err, label='random', capsize=4)
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
    # allow optional first CLI arg to set how many latest runs per mode to include (default 50)
    try:
        n = int(sys.argv[1]) if len(sys.argv) > 1 else 50
    except Exception:
        n = 50
    by_mode = pick_latest_n_by_mode(runs, n=n)
    adaptive_runs = by_mode.get('adaptive', [])
    random_runs = by_mode.get('random', [])
    if not adaptive_runs or not random_runs:
        print(f'Need at least one adaptive and one random run to compare. Found modes: {list(by_mode.keys())}')
        return

    # aggregate metrics across runs
    agg_adaptive = aggregate_runs_metrics(adaptive_runs)
    agg_random = aggregate_runs_metrics(random_runs)

    # build per-metric comparison summary
    keys = set(agg_adaptive.keys()) | set(agg_random.keys())
    perMetric = {}
    diffs = {}
    for k in keys:
        a = agg_adaptive.get(k, { 'mean': None, 'std': None, 'count': 0, 'total': 0 })
        b = agg_random.get(k, { 'mean': None, 'std': None, 'count': 0, 'total': 0 })
        mean_a = a.get('mean')
        mean_b = b.get('mean')
        diff = None
        try:
            if mean_a is not None and mean_b is not None:
                diff = float(mean_b) - float(mean_a)
        except Exception:
            diff = None
        perMetric[k] = {
            'adaptive': {'mean': a.get('mean'), 'std': a.get('std'), 'count': a.get('count'), 'total': a.get('total')},
            'random': {'mean': b.get('mean'), 'std': b.get('std'), 'count': b.get('count'), 'total': b.get('total')},
            'diff': diff
        }
        diffs[k] = diff

    derived = {
        'fairnessImprovement': None,
        'stabilityGain': None
    }
    # derived using means if available
    try:
        if 'avgDistanceToCenter' in perMetric:
            a_mean = perMetric['avgDistanceToCenter']['adaptive']['mean']
            r_mean = perMetric['avgDistanceToCenter']['random']['mean']
            if a_mean is not None and r_mean is not None:
                derived['fairnessImprovement'] = a_mean - r_mean
        if 'killVariance' in perMetric:
            a_mean = perMetric['killVariance']['adaptive']['mean']
            r_mean = perMetric['killVariance']['random']['mean']
            if a_mean is not None and r_mean is not None:
                derived['stabilityGain'] = a_mean - r_mean
    except Exception:
        pass

    comp = { 'perMetric': perMetric, 'diffs': diffs, 'derived': derived }
    write_output(comp, adaptive_runs, random_runs)

if __name__ == '__main__':
    main()
