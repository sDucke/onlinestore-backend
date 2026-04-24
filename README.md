# onlinestore-backend

Este servicio ya no guarda credenciales en el repositorio. Todas las credenciales y endpoints sensibles se deben definir desde las variables de entorno del despliegue en Dokploy.

## Variables requeridas en Dokploy

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `OPENAI_API_KEY`
- `N8N_WEBHOOK_URL`
- `N8N_API_TOKEN`

## Variables opcionales

- `APP_UPLOAD_DIR` (default: `/root/images`)
- `SPRING_JPA_HIBERNATE_DDL_AUTO` (default: `update`)
- `SPRING_JPA_SHOW_SQL` (default: `true`)

Toma `.env.example` como referencia para los nombres esperados.

## Despliegue con volumen persistente

El repositorio incluye `Dockerfile` y `docker-compose.yml` para desplegar el backend con un volumen persistente montado en `/root/images`.

- La aplicacion guarda las imagenes en `APP_UPLOAD_DIR`, que por defecto apunta a `/root/images`.
- `docker-compose.yml` monta el volumen nombrado `backend_images` sobre `/root/images`, para que los archivos no se pierdan al recrear el contenedor.
- En Dokploy basta con desplegar este repositorio usando el `docker-compose.yml` incluido y definir las variables de entorno requeridas.
