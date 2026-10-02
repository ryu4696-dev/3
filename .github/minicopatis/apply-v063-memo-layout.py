from pathlib import Path

g = Path("ledgerapp/app/build.gradle")
s = g.read_text()
s = s.replace("versionCode 18", "versionCode 19")
s = s.replace("versionName '0.6.2'", "versionName '0.6.3'")
g.write_text(s)

print("MiniCoPaTis v0.6.3 memo-card layout version applied")
