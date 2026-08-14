# Guía de Autenticación y Testing - SiGEP API

## 🔐 Obtener Bearer Token para Swagger

### Usuarios de Prueba

En ambiente de **desarrollo**, se crean automáticamente los siguientes usuarios:

| Username   | Password     | Role     | Email                    |
|------------|--------------|----------|--------------------------|
| `admin`    | `password123`| ADMIN    | admin@sigep.edu.mx       |
| `teacher`  | `password123`| TEACHER  | teacher@sigep.edu.mx     |
| `guardian` | `password123`| GUARDIAN | guardian@sigep.edu.mx    |

---

## 📝 Cómo obtener un Token JWT

### Método 1: Usando Swagger UI

1. **Inicia la aplicación** en modo desarrollo:
   ```cmd
   gradlew.bat bootRun --args='--spring.profiles.active=dev'
   ```

2. **Abre Swagger** en tu navegador:
   ```
   http://localhost:8080/swagger-ui/index.html
   ```

3. **Busca el endpoint** `POST /api/v1/auth/login` en la sección **auth-controller**

4. **Haz click en "Try it out"**

5. **Pega este JSON** en el body:
   ```json
   {
     "username": "admin",
     "password": "password123"
   }
   ```

6. **Click en "Execute"**

7. **Copia el token** de la respuesta (campo `accessToken`)

8. **Autoriza en Swagger**:
   - Click en el botón **"Authorize" 🔒** (arriba a la derecha)
   - Pega el token en el campo **Value** con el formato: `Bearer {token}`
   - Click en **"Authorize"**
   - Click en **"Close"**

9. **¡Listo!** Ahora puedes probar todos los endpoints protegidos

---

### Método 2: Usando cURL

```bash
# Login para obtener token
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "password123"
  }'
```

**Respuesta esperada:**
```json
{
  "success": true,
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJhZG1pbiIsInVzZXJJZCI6MSwicm9sZSI6IkFETUlOIiwiaWF0IjoxNjk4MzQ1NjAwLCJleHAiOjE2OTg0MzIwMDB9.xxx",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "user": {
      "id": 1,
      "username": "admin",
      "email": "admin@sigep.edu.mx",
      "firstName": "Admin",
      "lastName": "Sistema",
      "role": "ADMIN"
    }
  },
  "message": "Login successful",
  "timestamp": "2025-10-22T18:00:00Z"
}
```

---

### Método 3: Usando Postman

1. **Crea una nueva request POST**:
   - URL: `http://localhost:8080/api/v1/auth/login`
   - Headers: `Content-Type: application/json`

2. **Body (raw JSON)**:
   ```json
   {
     "username": "admin",
     "password": "password123"
   }
   ```

3. **Send** → Copia el `accessToken`

4. **Para usar en otras requests**:
   - Ve a la pestaña **Authorization**
   - Type: **Bearer Token**
   - Token: Pega el `accessToken`

---

## 🎭 Tokens de Ejemplo por Rol

### Token de ADMIN
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'
```

### Token de TEACHER
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "teacher", "password": "password123"}'
```

### Token de GUARDIAN
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "guardian", "password": "password123"}'
```

---

## 🔄 Refresh Token

Si tu token expira (después de 24 horas), puedes renovarlo:

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh-token \
  -H "Content-Type: application/json" \
  -d '{
    "refreshToken": "tu-refresh-token-aqui"
  }'
```

---

## 🧪 Probar Endpoints Protegidos

Una vez autorizado, puedes probar endpoints como:

### Obtener todos los estudiantes (requiere ADMIN o TEACHER)
```bash
curl -X GET http://localhost:8080/api/v1/students \
  -H "Authorization: Bearer {tu-token-aqui}"
```

### Crear un estudiante (requiere ADMIN)
```bash
curl -X POST http://localhost:8080/api/v1/students \
  -H "Authorization: Bearer {tu-token-aqui}" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Juan",
    "lastName": "Pérez",
    "email": "juan.perez@example.com",
    "documentNumber": "12345678",
    "birthDate": "2010-05-15",
    "guardianId": null
  }'
```

---

## 🔧 Troubleshooting

### El token no funciona (401 Unauthorized)
- Verifica que copiaste el token completo (puede ser muy largo)
- Asegúrate de usar el formato: `Bearer {token}` (con espacio después de Bearer)
- Verifica que el token no haya expirado (24 horas de validez)

### No puedo hacer login
- Verifica que la aplicación esté corriendo en modo `dev`
- Confirma que los usuarios se crearon (revisa los logs al iniciar)
- Verifica la conexión a PostgreSQL

### Error 403 Forbidden
- El token es válido pero no tienes permisos para ese endpoint
- Usa un token con el rol adecuado (ej: usa `admin` para crear estudiantes)

---

## 📊 Estructura del Token JWT

El token JWT contiene esta información:

```json
{
  "sub": "admin",           // username
  "userId": 1,              // ID del usuario
  "role": "ADMIN",          // Rol del usuario
  "iat": 1698345600,        // Fecha de emisión
  "exp": 1698432000         // Fecha de expiración
}
```

---

## ⚠️ Notas Importantes

1. **Los usuarios de prueba solo se crean en modo `dev`**
2. **En producción, usa contraseñas seguras**
3. **Los tokens expiran después de 24 horas**
4. **El refresh token dura 7 días**
5. **No compartas tus tokens en repositorios públicos**

---

## 🚀 Inicio Rápido

```bash
# 1. Inicia la aplicación en modo dev
gradlew.bat bootRun --args='--spring.profiles.active=dev'

# 2. Obtén un token (en otra terminal)
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "password123"}'

# 3. Usa el token en tus requests
curl -X GET http://localhost:8080/api/v1/students \
  -H "Authorization: Bearer {copia-el-accessToken-aqui}"
```

---

## 📖 Más Información

- Ver `SECURITY.md` para detalles de autorización
- Ver `README.md` para configuración general
- Ver Swagger UI para documentación completa de la API

