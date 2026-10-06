from pathlib import Path
import re

build=Path("ledgerapp/app/build.gradle")
b=build.read_text()
b,c1=re.subn(r"versionCode\s+\d+","versionCode 41",b,count=1)
b,c2=re.subn(r"versionName\s+'[^']+'","versionName '1.9.5'",b,count=1)
if c1!=1 or c2!=1:
    raise SystemExit("version declarations not found")
build.write_text(b)
