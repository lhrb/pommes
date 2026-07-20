#!/usr/bin/env bash
set -Eeuo pipefail

app_dir=/opt/pommes
database="$app_dir/data/pommes-connection.db"
backup_dir="$app_dir/backups"

if [[ ! -f $database ]]; then
  echo "Database does not exist yet; skipping backup."
  exit 0
fi

timestamp=$(date -u +%Y%m%dT%H%M%SZ)
backup="$backup_dir/pommes-connection-$timestamp.db"

sqlite3 "$database" ".backup '$backup'"
gzip --force "$backup"
find "$backup_dir" -type f -name 'pommes-connection-*.db.gz' -mtime +14 -delete
