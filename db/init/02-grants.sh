#!/bin/sh
# Otorga a la cuenta de la app SOLO lo que el codigo realmente ejecuta.
#
# Se ejecuta despues de 01-schema.sql. Es idempotente: tras agregar una tabla
# o una operacion nueva se puede re-aplicar sin perder datos:
#   docker compose exec -T db sh < db/init/02-grants.sh
#
# Matriz de permisos (derivada de los repositorios/servicios del backend):
#
#   tabla      SELECT  INSERT  UPDATE (solo estas columnas)                 DELETE
#   users        si      si    password_hash, must_change_password,           no
#                              token_version
#   librarys     si      si    enabled                                        no
#   books        si      si    isbn, titulo, autor, portada_url, enabled      no
#   favorites    si      si    -                                              si
#
# Lo que la app NO tiene, a proposito:
#   - DDL (CREATE/ALTER/DROP) ni TRUNCATE: no es duena de ninguna tabla.
#   - UPDATE sobre users.role / users.enabled / users.email: aunque se
#     inyecte SQL, no se puede escalar una cuenta a ADMIN con un UPDATE.
#   - DELETE salvo en favorites: usuarios, librerias y libros solo tienen baja
#     logica (enabled=false), por diseno del negocio.
#   - TEMPORARY, REFERENCES, TRIGGER, ni acceso a otras bases.
#
# Las columnas de UPDATE dependen de @DynamicUpdate en las entidades User,
# Library y Book (Hibernate solo incluye en el UPDATE las columnas modificadas).

: "${POSTGRES_USER:?Falta POSTGRES_USER}"
: "${POSTGRES_DB:?Falta POSTGRES_DB}"
: "${DB_USER:?Falta DB_USER en .env}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --no-psqlrc \
     --dbname "$POSTGRES_DB" <<'EOSQL'
\getenv app_user DB_USER
\getenv db_name POSTGRES_DB

-- 1. Cerrar los permisos que PUBLIC (todos los roles) tiene por defecto.
REVOKE ALL ON DATABASE :"db_name" FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;

-- 2. Partir de cero para la cuenta de la app (idempotencia ante cambios).
REVOKE ALL ON ALL TABLES    IN SCHEMA public FROM :"app_user";
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM :"app_user";
REVOKE ALL ON SCHEMA public FROM :"app_user";
REVOKE ALL ON DATABASE :"db_name" FROM :"app_user";

-- 3. Conectarse y ver el schema (sin CREATE: no puede crear objetos).
GRANT CONNECT ON DATABASE :"db_name" TO :"app_user";
GRANT USAGE   ON SCHEMA public TO :"app_user";

-- 4. Permisos por tabla, segun la matriz de arriba.
GRANT SELECT, INSERT ON users, librarys, books, favorites TO :"app_user";

GRANT UPDATE (password_hash, must_change_password, token_version) ON users    TO :"app_user";
GRANT UPDATE (enabled)                                            ON librarys TO :"app_user";
GRANT UPDATE (isbn, titulo, autor, portada_url, enabled)          ON books    TO :"app_user";

GRANT DELETE ON favorites TO :"app_user";

-- Los ids son GENERATED ... AS IDENTITY: el INSERT alcanza, no hace falta
-- ningun permiso sobre secuencias.

-- 5. Tablas futuras: NO se otorga nada por defecto. Cada tabla nueva requiere
--    un GRANT explicito aqui (falla cerrado, no abierto).
EOSQL