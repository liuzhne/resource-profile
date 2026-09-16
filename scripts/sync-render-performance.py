#!/usr/bin/env python3
"""Apply only non-secret performance settings to existing Render services.

Dry run by default. Retains all directly set credentials in memory during the
required bulk env-var replacement; never prints, saves or rotates secrets.
Does not create resources, change plans, trigger deploys or bypass authorization.
"""
import argparse
import json
import os
from pathlib import Path
import requests
import yaml

KEYS = {
    'JAVA_TOOL_OPTIONS', 'SPRING_MAIN_LAZY_INITIALIZATION',
    'EDUCARE_PERFORMANCE_DATABASE_WARMUP', 'SPRING_MVC_SERVLET_LOAD_ON_STARTUP',
    'MYBATIS_PLUS_CONFIGURATION_LOG_IMPL', 'SERVER_COMPRESSION_ENABLED',
    'MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED', 'MANAGEMENT_HEALTH_REDIS_ENABLED',
    'DB_MAXIMUM_POOL_SIZE', 'DB_MINIMUM_IDLE', 'DB_MAX_LIFETIME_MS',
    'SPRING_AI_MCP_CLIENT_ENABLED', 'EDUCARE_MCP_DEFERRED_ENABLED',
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    spec = yaml.safe_load(Path('render.yaml').read_text())
    shared = {row['key']: str(row['value']) for group in spec['envVarGroups']
              for row in group['envVars'] if row.get('key') in KEYS and 'value' in row}
    session = requests.Session()
    session.headers['Authorization'] = 'Bearer ' + os.environ['RENDER_API_KEY']
    base = 'https://api.render.com/v1'

    def call(method, path, **kwargs):
        result = session.request(method, base + path, timeout=45, **kwargs)
        if not result.ok:
            raise RuntimeError(f'Render {method} {path}: HTTP {result.status_code}')
        return result.json() if result.content else None

    services = []
    cursor = None
    while True:
        rows = call('GET', '/services', params={'limit': 100, **({'cursor': cursor} if cursor else {})})
        services.extend(row['service'] for row in rows)
        if len(rows) < 100:
            break
        cursor = rows[-1]['cursor']
    by_name = {service['name']: service for service in services}
    evidence = []
    for definition in spec['services']:
        if definition.get('runtime') != 'docker' or not definition['name'].startswith('edu-portrait-'):
            continue
        # Python and legacy datastores have no JAVA_TOOL_OPTIONS and are unchanged.
        direct = {row['key']: str(row['value']) for row in definition.get('envVars', [])
                  if row.get('key') in KEYS and 'value' in row}
        if 'JAVA_TOOL_OPTIONS' not in direct:
            continue
        service = by_name[definition['name']]
        desired = {**shared, **direct, 'SPRING_MAIN_LAZY_INITIALIZATION': 'false'}
        identifier = service['id']
        previous = {}
        cursor = None
        while True:
            rows = call('GET', f'/services/{identifier}/env-vars', params={'limit': 100, **({'cursor': cursor} if cursor else {})})
            previous.update({row['envVar']['key']: row['envVar']['value'] for row in rows})
            if len(rows) < 100:
                break
            cursor = rows[-1]['cursor']
        changes = {key: {'previous': previous.get(key), 'desired': value}
                   for key, value in desired.items() if previous.get(key) != value}
        health = definition.get('healthCheckPath')
        old_health = service.get('serviceDetails', {}).get('healthCheckPath')
        if args.apply:
            if changes:
                merged = {**previous, **desired}
                call('PUT', f'/services/{identifier}/env-vars', json=[{'key': key, 'value': value} for key, value in merged.items()])
            if health and old_health != health:
                call('PATCH', f'/services/{identifier}', json={'serviceDetails': {'healthCheckPath': health}})
            # Re-read only the requested settings before deployment.
            actual_rows = call('GET', f'/services/{identifier}/env-vars', params={'limit': 100})
            actual = {row['envVar']['key']: row['envVar']['value'] for row in actual_rows}
            if any(actual.get(key) != value for key, value in desired.items()):
                raise RuntimeError(f'{definition["name"]}: performance setting verification failed')
        row = {'service': definition['name'], 'id': identifier, 'applied': args.apply,
               'changes': changes, 'previous_health': old_health, 'health': health}
        evidence.append(row)
        print(json.dumps(row, ensure_ascii=False), flush=True)
    Path(args.output).write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')

if __name__ == '__main__':
    main()
