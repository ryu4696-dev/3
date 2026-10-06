from pathlib import Path
import re

root = Path("ledgerapp/app/src/main/java/jp/co/ichika/salesledger")

# Launch the new quote workspace from MiniCoPaTis.
p = root / "MainActivity.kt"
s = p.read_text()
old = 'startActivity(Intent().setClassName(packageName, "jp.co.kobayashi.cardboardquote.MainActivity"))'
new = 'startActivity(Intent(this, QuoteWorkspaceActivity::class.java))'
if old not in s:
    raise SystemExit("quick quote launcher anchor missing")
s = s.replace(old, new, 1)
p.write_text(s)

# Register quote workspace/history screens.
manifest = Path("ledgerapp/app/src/main/AndroidManifest.xml")
m = manifest.read_text()
entries = '''        <activity
            android:name=".QuoteHistoryActivity"
            android:screenOrientation="unspecified"
            android:exported="false" />
        <activity
            android:name=".QuoteWorkspaceActivity"
            android:screenOrientation="unspecified"
            android:exported="false" />
'''
if 'android:name=".QuoteWorkspaceActivity"' not in m:
    marker = '    </application>'
    if marker not in m:
        raise SystemExit("manifest application end missing")
    m = m.replace(marker, entries + marker, 1)
manifest.write_text(m)

# Version.
build = Path("ledgerapp/app/build.gradle")
b = build.read_text()
b, c1 = re.subn(r"versionCode\s+\d+", "versionCode 36", b, count=1)
b, c2 = re.subn(r"versionName\s+'[^']+'", "versionName '1.9.0'", b, count=1)
if c1 != 1 or c2 != 1:
    raise SystemExit("version declarations not found")
build.write_text(b)
