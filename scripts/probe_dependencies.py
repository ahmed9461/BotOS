"""Read official publication metadata. Diagnostic only; never changes dependencies."""
from concurrent.futures import ThreadPoolExecutor
import json
from pathlib import Path
import re
import tomllib
from urllib.request import urlopen
import xml.etree.ElementTree as ET

GOOGLE = 'https://dl.google.com/dl/android/maven2/'
CENTRAL = 'https://repo.maven.apache.org/maven2/'
ARTIFACTS = {
    'agp': (GOOGLE, 'com/android/tools/build/gradle'),
    'kotlin': (CENTRAL, 'org/jetbrains/kotlin/kotlin-gradle-plugin'),
    'composeBom': (GOOGLE, 'androidx/compose/compose-bom'),
    'activity': (GOOGLE, 'androidx/activity/activity-compose'),
    'lifecycle': (GOOGLE, 'androidx/lifecycle/lifecycle-runtime-compose'),
    'navigation3': (GOOGLE, 'androidx/navigation3/navigation3-ui'),
    'datastore': (GOOGLE, 'androidx/datastore/datastore-preferences'),
    'coroutines': (CENTRAL, 'org/jetbrains/kotlinx/kotlinx-coroutines-android'),
    'media3': (GOOGLE, 'androidx/media3/media3-exoplayer'),
    'lottie': (CENTRAL, 'com/airbnb/android/lottie'),
}

def describe_metadata(xml, selected):
    versions = [e.text or '' for e in ET.fromstring(xml).findall('./versioning/versions/version')]
    stable = [v for v in versions if re.fullmatch(r'\d+(?:\.\d+)+', v)]
    return {'selected': selected, 'published': selected in versions, 'recent_stable': stable[-8:]}

def probe(item):
    key, version = item
    root, artifact = ARTIFACTS[key]
    url = root + artifact + '/maven-metadata.xml'
    try:
        with urlopen(url, timeout=25) as response:
            data = response.read(4_000_001)
        if len(data) > 4_000_000:
            raise ValueError('Metadata exceeds diagnostic size limit')
        result = describe_metadata(data, version)
    except Exception as error:
        result = {'selected': version, 'published': None, 'error': f'{type(error).__name__}: {error}'}
    return key, {'source': url, **result}

if __name__ == '__main__':
    root = Path(__file__).resolve().parents[1]
    versions = tomllib.loads((root / 'gradle/libs.versions.toml').read_text())['versions']
    with ThreadPoolExecutor(max_workers=4) as pool:
        results = dict(pool.map(probe, ((k, versions[k]) for k in ARTIFACTS)))
    print(json.dumps(results, indent=2, ensure_ascii=False))
    # Missing selected versions are evidence, not an instruction to downgrade.
    # Unknown metadata is a failed probe, never evidence that a version does not exist.
    raise SystemExit(1 if any(v['published'] is not True for v in results.values()) else 0)
