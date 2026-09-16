#!/usr/bin/env python3
"""Sequential, persistent-connection, read-only production latency acceptance.

Credentials stay in environment/memory. Reports contain no tokens or response records.
Run once before deployment and once after; sample 1 includes first-connection overhead.
"""
import argparse
import datetime
import json
import os
from pathlib import Path
import time
import requests

ADMIN_PATHS = [
    '/auth/userInfo', '/user/list?page=1&size=10',
    '/data/dashboard/statistics', '/data/dashboard/trend?period=week',
    '/data/dashboard/trend?period=month', '/data/dashboard/trend?period=year',
    '/data/dashboard/distribution', '/data/dashboard/recentLogins',
    '/teacher/list?pageNum=1&pageSize=10', '/teacher/1',
    '/student/list?pageNum=1&pageSize=10', '/student/1',
    '/student/1/academic', '/student/1/attendance/summary',
    '/mental/overview', '/mental/analysis',
    '/mental/questionnaires?pageNum=1&pageSize=10', '/mental/questionnaires/1/full',
    '/mental/questionnaires/1/completion', '/agent/api/v1/task/list?pageNum=1&pageSize=10',
]
ROLE_PATHS = {
    'teacher': ['/auth/userInfo', '/mental/overview'],
    'student': ['/auth/userInfo', '/mental/student/questionnaires?userId=3', '/mental/student/assessments?userId=3'],
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', default='https://edu-portrait-frontend.onrender.com/api')
    parser.add_argument('--role', choices=['admin', 'teacher', 'student'], default='admin')
    parser.add_argument('--rounds', type=int, default=5)
    parser.add_argument('--timeout', type=float, default=60)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    password = os.environ['BENCH_PASSWORD']
    rows = []
    with requests.Session() as session:
        def probe(path, run, method='GET', payload=None, login=False):
            stamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
            start = time.perf_counter()
            response = None
            try:
                response = session.request(method, args.base.rstrip('/') + path,
                                           json=payload, timeout=args.timeout)
                elapsed = (time.perf_counter() - start) * 1000
                try:
                    body = response.json()
                    code = body.get('code') if isinstance(body, dict) else None
                except ValueError:
                    body, code = {}, None
                row = {'path': path, 'method': method, 'run': run, 'utc': stamp,
                       'http': response.status_code, 'code': code, 'ms': round(elapsed, 2),
                       'server_timing': response.headers.get('Server-Timing'),
                       'ok': response.status_code == 200 and code == 200}
                if login and row['ok']:
                    session.headers['Authorization'] = 'Bearer ' + body['data']['token']
                # Body/records are deliberately never persisted or printed.
            except requests.RequestException as error:
                row = {'path': path, 'method': method, 'run': run, 'utc': stamp,
                       'ms': round((time.perf_counter() - start) * 1000, 2),
                       'ok': False, 'error_type': type(error).__name__}
            finally:
                if response is not None:
                    response.close()
            row['under_500ms'] = row['ok'] and row['ms'] <= 500
            rows.append(row)
            print(json.dumps(row, ensure_ascii=False), flush=True)
            return row['ok']

        if probe('/auth/login', 1, 'POST', {'username': os.environ.get('BENCH_USERNAME', args.role), 'password': password}, login=True):
            paths = ADMIN_PATHS if args.role == 'admin' else ROLE_PATHS[args.role]
            for path in paths:
                for run in range(1, args.rounds + 1):
                    probe(path, run)
        Path(args.output).write_text(json.dumps({'role': args.role, 'base': args.base,
            'measurement': 'wall-clock full response, persistent requests.Session, sequential',
            'rows': rows}, ensure_ascii=False, indent=2) + '\n')

if __name__ == '__main__':
    main()
