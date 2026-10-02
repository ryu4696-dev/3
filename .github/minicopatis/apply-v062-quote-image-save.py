from pathlib import Path

g = Path("ledgerapp/app/build.gradle")
s = g.read_text()
s = s.replace("versionCode 17", "versionCode 18")
s = s.replace("versionName '0.6.1'", "versionName '0.6.2'")
g.write_text(s)

print("MiniCoPaTis v0.6.2 quote memo/image-save version applied")
