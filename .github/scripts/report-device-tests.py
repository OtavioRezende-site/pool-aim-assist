"""Publish instrumented-test failures without requiring access to raw CI logs."""
from pathlib import Path
import os
import xml.etree.ElementTree as ET

reports = list(Path('app/build/outputs/androidTest-results').glob('connected/**/*.xml'))
tests = failures = skipped = 0
messages = []
for report in reports:
    root = ET.parse(report).getroot()
    for case in root.iter('testcase'):
        tests += 1
        if case.find('skipped') is not None:
            skipped += 1
        for kind in ('failure', 'error'):
            problem = case.find(kind)
            if problem is None:
                continue
            failures += 1
            name = f"{case.get('classname')}.{case.get('name')}"
            detail = (problem.get('message') or problem.text or kind)[:1800]
            message = f'{name}: {detail}'
            escaped = message.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
            print(f'::error title=Teste instrumentado::{escaped}')
            messages.append(message)
if not reports:
    print('::warning::Nenhum XML de instrumentação disponível; consulte a inicialização do emulador no log do job.')
summary = f'Testes instrumentados: {tests}; falhas/erros: {failures}; ignorados: {skipped}.\n'
print(summary)
if os.environ.get('GITHUB_STEP_SUMMARY'):
    with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
        output.write(summary)
        for message in messages:
            output.write(f'\n```text\n{message}\n```\n')
