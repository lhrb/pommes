#!/usr/bin/env bash
set -Eeuo pipefail

app_dir=/opt/pommes

log() {
  printf '\n==> %s\n' "$*"
}

require_root() {
  [[ "${EUID}" -eq 0 ]] || die "run this script as root"
}

install_docker() {
  log "Installing Docker"

  apt update
  apt upgrade -y
  apt install -y ca-certificates curl ufw nano htop jq openssl sqlite3

  apt remove -y docker.io docker-compose docker-compose-v2 docker-doc podman-docker containerd runc 2>/dev/null || true

  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc

  cat >/etc/apt/sources.list.d/docker.sources <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

  apt update
  apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
  systemctl enable --now docker
}

configure_firewall() {
  log "Configuring firewall"
  ufw default deny incoming
  ufw default allow outgoing

  ufw allow 22/tcp
  ufw allow 80/tcp
  ufw allow 443/tcp

  ufw --force enable
  ufw status verbose
}

configure_app() {
  log "Creating application directories"
  install -d -o "$app_user" -g "$app_user" -m 0750 "$app_dir" "$app_dir/data" "$app_dir/releases" "$app_dir/backups"
  install -d -o 10001 -g 10001 -m 0750 "$app_dir/data"
  install -m 0750 -o "$app_user" -g "$app_user" infra/deploy.sh "$app_dir/deploy.sh"
  install -m 0750 -o "$app_user" -g "$app_user" infra/backup-db.sh "$app_dir/backup-db.sh"
  install -m 0640 -o "$app_user" -g "$app_user" infra/compose.yml "$app_dir/compose.yml"
  install -m 0640 -o "$app_user" -g "$app_user" infra/Caddyfile "$app_dir/Caddyfile"

  if [[ ! -f $app_dir/.env ]]; then
    install -m 0640 -o "$app_user" -g "$app_user" infra/.env.example "$app_dir/.env"
    session_key=$(openssl rand -base64 16)
    sed -i "s|^SESSION_KEY=$|SESSION_KEY=$session_key|" "$app_dir/.env"
    log "Edit $app_dir/.env and set DOMAIN and ACME_EMAIL before deploying"
  fi

  usermod -aG docker "$app_user"
}

install_backup_timer() {
  log "Installing daily database backup timer"
  install -m 0644 infra/pommes-backup.service /etc/systemd/system/pommes-backup.service
  install -m 0644 infra/pommes-backup.timer /etc/systemd/system/pommes-backup.timer
  systemctl daemon-reload
  systemctl enable --now pommes-backup.timer
}

require_root
install_docker
configure_firewall
# configure_app
# install_backup_timer

log "Server setup complete. Log out and back in before using Docker as $app_user."
