#!/usr/bin/env bash
set -Eeuo pipefail

app_dir=/opt/pommes
release=${1:?Usage: deploy.sh <git-sha>}
archive="$app_dir/releases/$release.tar.gz"
image="pommes-connection:$release"

log() {
  printf '\n==> %s\n' "$*"
}

if [[ ! $release =~ ^[0-9a-f]{7,64}$ ]]; then
  echo "Release must be a Git SHA." >&2
  exit 1
fi

if [[ ! -f $archive ]]; then
  echo "Missing release archive: $archive" >&2
  exit 1
fi

cd "$app_dir"

domain=$(sed -n 's/^DOMAIN=//p' .env | tail -n 1)
acme_email=$(sed -n 's/^ACME_EMAIL=//p' .env | tail -n 1)
if [[ -z $domain || $domain == example.com || -z $acme_email || $acme_email == admin@example.com ]]; then
  echo "Set DOMAIN and ACME_EMAIL in $app_dir/.env before deploying." >&2
  exit 1
fi

log "Loading $image"
docker load --input "$archive"
docker image inspect "$image" >/dev/null

previous_image=$(grep '^IMAGE_TAG=' .env | cut -d= -f2- || true)
if grep -q '^IMAGE_TAG=' .env; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$release/" .env
else
  printf '\nIMAGE_TAG=%s\n' "$release" >>.env
fi

log "Starting release $release"
docker compose --project-name pommes --file compose.yml up -d --remove-orphans

for _ in $(seq 1 30); do
  if [[ $(docker inspect --format '{{.State.Health.Status}}' pommes-app-1 2>/dev/null || true) == healthy ]]; then
    log "Release $release is healthy"
    find "$app_dir/releases" -type f -name '*.tar.gz' -mtime +14 -delete
    docker image prune -f
    exit 0
  fi
  sleep 2
done

echo "Release $release did not become healthy." >&2
if [[ -n $previous_image ]]; then
  sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=$previous_image/" .env
  docker compose --project-name pommes --file compose.yml up -d --remove-orphans
fi
exit 1
