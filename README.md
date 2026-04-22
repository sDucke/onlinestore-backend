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

- `APP_UPLOAD_DIR` (default: `uploads/images`)
- `SPRING_JPA_HIBERNATE_DDL_AUTO` (default: `update`)
- `SPRING_JPA_SHOW_SQL` (default: `true`)

Toma `.env.example` como referencia para los nombres esperados.
