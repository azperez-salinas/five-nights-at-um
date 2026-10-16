#!/bin/sh
# Verifica el principio de minimo privilegio de la cuenta de la app, contra la
# base real. Sirve como evidencia del requisito.
#
# Uso (con el stack levantado):
#   docker compose exec -T db sh < db/verify-privileges.sh
#
# Corre con la cuenta administrativa y usa SET LOCAL ROLE para actuar como la
# cuenta de la app. Todo se hace dentro de una transaccion que se revierte:
# no deja datos.

: "${POSTGRES_USER:?}" "${POSTGRES_DB:?}" "${DB_USER:?}"

PASS=0
FAIL=0

run_as_app() {
    # $1 = sentencia SQL. Devuelve la salida (stdout+stderr) de psql.
    psql --username "$POSTGRES_USER" --no-password --no-psqlrc --dbname "$POSTGRES_DB" \
         -v app_user="$DB_USER" -X -q -t -A 2>&1 <<EOSQL
BEGIN;
SET LOCAL ROLE :"app_user";
$1;
ROLLBACK;
EOSQL
}

expect_denied() {
    out=$(run_as_app "$2")
    if echo "$out" | grep -qiE "permission denied|must be owner"; then
        PASS=$((PASS + 1)); echo "  OK    denegado : $1"
    else
        FAIL=$((FAIL + 1)); echo "  FALLA permitido: $1"; echo "        -> $out"
    fi
}

expect_allowed() {
    out=$(run_as_app "$2")
    if echo "$out" | grep -qiE "permission denied|must be owner|ERROR"; then
        FAIL=$((FAIL + 1)); echo "  FALLA denegado : $1"; echo "        -> $out"
    else
        PASS=$((PASS + 1)); echo "  OK    permitido: $1"
    fi
}

echo "== Atributos del rol '$DB_USER' =="
admin_query() {
    # -c no interpola variables de psql; por stdin si.
    psql --username "$POSTGRES_USER" --no-password --no-psqlrc --dbname "$POSTGRES_DB" \
         -v app_user="$DB_USER" -X -q -t -A -F '|' 2>&1
}

attrs=$(admin_query <<'EOSQL'
SELECT rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
FROM pg_roles WHERE rolname = :'app_user';
EOSQL
)
if [ "$attrs" = "f|f|f|f|f" ]; then
    PASS=$((PASS + 1)); echo "  OK    sin superuser/createdb/createrole/replication/bypassrls"
else
    FAIL=$((FAIL + 1)); echo "  FALLA atributos inesperados: '$attrs' (rol inexistente o con privilegios)"
fi

owned=$(admin_query <<'EOSQL'
SELECT count(*) FROM pg_tables WHERE schemaname = 'public' AND tableowner = :'app_user';
EOSQL
)
if [ "$owned" = "0" ]; then
    PASS=$((PASS + 1)); echo "  OK    no es duena de ninguna tabla"
else
    FAIL=$((FAIL + 1)); echo "  FALLA es duena de $owned tabla(s): podria hacer ALTER/DROP"
fi

echo "== Operaciones que la app SI necesita =="
expect_allowed "SELECT users"                 "SELECT id, password_hash FROM users LIMIT 1"
expect_allowed "INSERT users (registro)"      "INSERT INTO users (username, email, password_hash, role, enabled, must_change_password, token_version, created_at) VALUES ('verif_x', 'verif_x@example.com', 'h', 'COMPRADOR', true, false, 0, now())"
expect_allowed "UPDATE users.password_hash"   "UPDATE users SET password_hash = 'x', must_change_password = false, token_version = token_version + 1 WHERE false"
expect_allowed "UPDATE librarys.enabled"      "UPDATE librarys SET enabled = false WHERE false"
expect_allowed "UPDATE books (edicion/baja)"  "UPDATE books SET titulo = 't', autor = 'a', isbn = 'i', portada_url = 'p', enabled = false WHERE false"
expect_allowed "INSERT/DELETE favorites"      "INSERT INTO favorites (user_id, book_id, created_at) SELECT 1, 1, now() WHERE false; DELETE FROM favorites WHERE false"

echo "== Operaciones que la app NO debe poder hacer =="
expect_denied "UPDATE users.role (escalar a ADMIN)"   "UPDATE users SET role = 'ADMIN' WHERE false"
expect_denied "UPDATE users.enabled"                  "UPDATE users SET enabled = true WHERE false"
expect_denied "UPDATE users.email"                    "UPDATE users SET email = 'x' WHERE false"
expect_denied "UPDATE books.library_id (mover libro)" "UPDATE books SET library_id = 1 WHERE false"
expect_denied "UPDATE favorites"                      "UPDATE favorites SET book_id = 1 WHERE false"
expect_denied "DELETE users"                          "DELETE FROM users WHERE false"
expect_denied "DELETE librarys"                       "DELETE FROM librarys WHERE false"
expect_denied "DELETE books"                          "DELETE FROM books WHERE false"
expect_denied "TRUNCATE favorites"                    "TRUNCATE favorites"
expect_denied "DROP TABLE users"                      "DROP TABLE users"
expect_denied "ALTER TABLE users"                     "ALTER TABLE users ADD COLUMN x int"
expect_denied "CREATE TABLE (schema public)"          "CREATE TABLE verif_x (id int)"
expect_denied "CREATE TEMP TABLE"                     "CREATE TEMP TABLE verif_x (id int)"
expect_denied "CREATE ROLE"                           "CREATE ROLE verif_x"
expect_denied "CREATE EXTENSION"                      "CREATE EXTENSION IF NOT EXISTS pgcrypto"
expect_denied "COPY ... TO PROGRAM (ejecutar SO)"     "COPY (SELECT 1) TO PROGRAM 'id'"
expect_denied "pg_read_file (leer archivos del SO)"   "SELECT pg_read_file('/etc/passwd')"

echo
echo "Resultado: $PASS OK, $FAIL con falla"
[ "$FAIL" -eq 0 ]