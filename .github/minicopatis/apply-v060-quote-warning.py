from pathlib import Path
import shutil

root = Path("ledgerapp")
source = Path(".github/minicopatis/MiniCoPaTisApplication.kt")
dest = root / "app/src/main/java/jp/co/ichika/salesledger/MiniCoPaTisApplication.kt"
dest.parent.mkdir(parents=True, exist_ok=True)
shutil.copyfile(source, dest)

manifest = root / "app/src/main/AndroidManifest.xml"
text = manifest.read_text()
needle = "    <application\n"
replacement = "    <application\n        android:name=\".MiniCoPaTisApplication\"\n"
if 'android:name=".MiniCoPaTisApplication"' not in text:
    if needle not in text:
        raise SystemExit("application tag not found")
    text = text.replace(needle, replacement, 1)
manifest.write_text(text)

print("MiniCoPaTis quote warning application hook installed")
