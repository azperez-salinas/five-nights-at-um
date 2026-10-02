# Los libros de Buysan
Repositorio para el proyecto de la materia Desarrollo Seguro

## ¿Cómo levantar el proyecto?

Requisitos: Docker Desktop instalado y corriendo.

### 1. Variables de entorno

Copie `.env.example` a `.env` en la raíz del repo y complete con los valores del archivo proporcionado en la entrega nro. 3

### 2. Levantar el stack

```bash
docker compose up --build -d
```

Esto levanta tres contenedores:

| Servicio | Puerto | URL |
|---|---|---|
| Frontend | 5173 | http://localhost:5173 |
| Backend | 8080 | http://localhost:8080 |
| Postgres | 5432 | `localhost:5432` (expuesto solo para desarrollo local, ej. conectar con DBeaver) |

### 3. Datos de prueba

La primera vez que arranca contra una base vacía, el backend carga automáticamente:
- 3 librerías de prueba (2 habilitadas: "Libreria Central" y "Libreria Norte"; 1 deshabilitada: "Libreria Dada de Baja"), cada una con su propio catálogo completo de 202 libros (606 libros en total).
- Los libros de la librería deshabilitada sirven para probar que dejan de verse en catálogo/búsqueda/detalle sin borrarse de la base.
- A futuro esto se implementara de otra manera (dueño carga el CSV de los libros - aún no implementado)
 
Para usar funcionalidades de comprador (favoritos, etc.) hay que registrar un usuario:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"tu_usuario","email":"tu@email.com","password":"tu_password"}'
```

### 4. Reiniciar desde cero

Si cambia el modelo de datos y hace falta recrear la base:

```bash
docker compose down -v   # borra tambien el volumen de Postgres
docker compose up --build -d
```
