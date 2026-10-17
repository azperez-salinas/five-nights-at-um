#!/bin/sh
# Crea (o actualiza) la cuenta de base de datos que usa el backend.
#
# Lo ejecuta el entrypoint de la imagen postgres la PRIMERA vez que arranca
# con un volumen vacio, como la cuenta administrativa (POSTGRES_USER). Tambien
# se puede re-ejecutar a mano para rotar la contrasena de la app:
#   docker compose exec -T db sh < db/init/00-create-app-role.sh
#
# Principio de minimo privilegio: el rol se crea SIN ningun atributo
# privilegiado. Los permisos sobre tablas los otorga 02-grants.sh.

: "${POSTGRES_USER:?Falta POSTGRES_USER}"
: "${POSTGRES_DB:?Falta POSTGRES_DB}"
: "${DB_USER:?Falta DB_USER en .env}"
: "${DB_PASSWORD:?Falta DB_PASSWORD en .env}"

# La app nunca debe conectarse con la cuenta administrativa ni con "postgres".
if [ "$DB_USER" = "$POSTGRES_USER" ] || [ "$DB_USER" = "postgres" ]; then
    echo "ERROR: DB_USER no puede ser la cuenta administrativa ni 'postgres'." >&2
    exit 1
fi

# \getenv lee las variables desde el entorno del proceso: la contrasena no
# aparece en la linea de comandos (ps) y %I / %L escapan identificador y literal.
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --no-psqlrc \
     --dbname "$POSTGRES_DB" <<'EOSQL'
\getenv app_user DB_USER
\getenv app_password DB_PASSWORD

SELECT format('CREATE ROLE %I', :'app_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_user')
\gexec

-- Siempre se (re)aplican los atributos: si alguien los cambio a mano, vuelven
-- a quedar restringidos. SCRAM-SHA-256 es el cifrado por defecto en PG 14+.
SELECT format(
    'ALTER ROLE %I WITH LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS INHERIT CONNECTION LIMIT 20',
    :'app_user', :'app_password')
\gexec
EOSQL