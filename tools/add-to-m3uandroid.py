"""Add an INDEPENDENT module to an existing clone; never patch original screens.
Usage: python tools/add-to-m3uandroid.py /path/to/M3UAndroid
The target is changed only after validation. An existing module is not overwritten.
"""
from pathlib import Path
import argparse, shutil

def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('repository', type=Path)
    args = ap.parse_args()
    root = args.repository.resolve()
    settings = root/'settings.gradle.kts'
    module = root/'app/lidertv'
    template = Path(__file__).resolve().parents[1]
    if not settings.is_file() or not (root/'app/smartphone').is_dir() or not (root/'app/tv').is_dir():
        raise SystemExit('M3UAndroid root with app/smartphone and app/tv not found.')
    if module.exists():
        raise SystemExit('app/lidertv already exists; nothing overwritten.')
    text = settings.read_text(encoding='utf-8')
    if ':app:lidertv' in text:
        raise SystemExit('settings already contains :app:lidertv; inspect manually.')
    backup = root/'settings.gradle.kts.lidertv-backup'
    if backup.exists():
        raise SystemExit('Backup already exists; inspect it before running again.')
    shutil.copytree(template/'app', module, ignore=shutil.ignore_patterns('build','.gradle'))
    try:
        build = (module/'build.gradle').read_text(encoding='utf-8')
        build = build.replace("plugins { id 'com.android.application' }", 'plugins { alias(libs.plugins.com.android.application) }')
        (module/'build.gradle').write_text(build,encoding='utf-8')
        shutil.copy2(settings, backup)
        settings.write_text(text+'\n// LiderTv independent prototype module\ninclude(":app:lidertv")\n',encoding='utf-8')
        shutil.copy2(template/'LICENSE', module/'LICENSE')
        shutil.copy2(template/'NOTICE.md', module/'NOTICE.md')
    except Exception:
        if backup.exists(): shutil.copy2(backup, settings)
        shutil.rmtree(module)
        raise
    print('New module added: :app:lidertv')
    print('Original phone/TV screens and database were NOT changed or migrated.')
    print('Use the original wrapper: ./gradlew :app:lidertv:assembleDebug')
    print('This integration has not been built against the original repository.')

if __name__ == '__main__': main()
