set -eu

export PGDATA=/tmp/pgdata
export POSTGRES_USER=sandbox
export POSTGRES_DB=sandboxdb
export HOME=/tmp
export PSQL_HISTORY=/tmp/.psql_history

cleanup() {
  pg_ctl -D "$PGDATA" -m fast -w stop >/dev/null 2>/dev/null || true
  rm -rf "$PGDATA"
}
trap cleanup EXIT

if [ ! -f "$SQL_FILE" ]; then
  echo "SQL file not found: $SQL_FILE" >&2
  exit 66
fi

# Init database
mkdir -p "$PGDATA"
initdb -U "$POSTGRES_USER" -A trust >/dev/null

# Make it super local-only
echo "listen_addresses=''" >> "$PGDATA/postgresql.conf"
echo "unix_socket_directories='/tmp'" >> "$PGDATA/postgresql.conf"

# Trust local socket connections (inside container only)
echo "local all all trust" > "$PGDATA/pg_hba.conf"

# Start Postgres
pg_ctl -D "$PGDATA" -o "-k /tmp" -w start >/dev/null

# Ensure target database exists
createdb -h /tmp -U "$POSTGRES_USER" "$POSTGRES_DB" >/dev/null

# Run SQL script and emit results to stdout as CSV (quiet mode).
# Measure execution time via \timing output (captured from stderr) without extra DB roundtrips.
timing_file=$(mktemp)
output_file=$(mktemp)
if psql -h /tmp -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 --csv -P pager=off -P footer=off -q > "$output_file" 2> "$timing_file" <<PSQL; then
\\timing on
\\i $SQL_FILE
PSQL
  status=0
else
  status=$?
fi
time_ms=$(awk '/Time:/{ms=$2} END{if (ms=="") ms=0; printf "%.0f", ms}' "$timing_file")
if [ -s "$timing_file" ]; then
  cat "$timing_file" >&2
fi
sed '/^Time:/d' "$output_file"
# Clean up temporary files created inside the container.
rm -f "$timing_file" "$output_file"
printf "\n__EXECUTION_TIME__: %s\n" "$time_ms"
exit "$status"
