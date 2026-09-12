# Field log analyzer (dev only)

Not part of the product JAR. Parses ItemGuard forensic JSONL (`schemaVersion` 1 or 2) from a zip dump.

```bat
gradlew.bat fieldLogAnalysis
gradlew.bat fieldLogAnalysis -PFieldLogZip=D:\path\itemguard-log.zip
```

`itemguard-log.zip` and `analysis/field-false-positive-report.*` are the **PRE-HARDENING BASELINE**. Do not overwrite them with post-hardening Field Test dumps.

After a new Field Test zip:

```bat
gradlew.bat fieldValidationCompare -PFieldLogZip=D:\path\post-hardening-logs.zip
```

Writes `analysis/field-validation-after.*` and `analysis/field-validation-compare.*`.

Or:

```bat
set ITEMGUARD_FIELD_LOG_ZIP=D:\path\itemguard-log.zip
node tools/field-log-analyzer/analyze.js
```

Writes anonymized reports to `analysis/`. Real `itemguard-log.zip` is gitignored.
