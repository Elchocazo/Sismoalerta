# SismoAlerta - Backend de Monitoreo SGC y Push FCM

Este directorio contiene el servicio de monitoreo en tiempo real del **Servicio Geológico Colombiano (SGC)** y el despachador de notificaciones push de alta prioridad vía **Firebase Cloud Messaging (FCM)**.

---

## 🌟 Arquitectura

1. **Monitoreo Continuo**: Consulta cada 30 segundos el endpoint oficial de sismos con intensidad del SGC:
   `https://sismosentido.sgc.gov.co/rest/resumenSismosConIntensidadBatch/200`
2. **Detección y Deduplicación**: Mantiene una lista en memoria y en disco (`known_sismos.json`) para nunca enviar sismos repetidos.
3. **Despacho Push FCM de Alta Prioridad**: Al detectar un sismo nuevo con magnitud $\ge 2.0$, despacha un mensaje al tópico `sismos_colombia` con:
   ```json
   {
     "topic": "sismos_colombia",
     "android": { "priority": "high" },
     "data": {
       "id": "SGC-...",
       "magnitude": "4.5",
       "epicenter": "Los Santos - Santander",
       "latitude": "6.78",
       "longitude": "-73.12",
       "depthKm": "145.0",
       "timestamp": "1725650000000",
       "source": "Servicio Geológico Colombiano (SGC)",
       "status": "CONFIRMED"
     }
   }
   ```
4. **Despertar en el Teléfono**: La app Android (`SismoFirebaseMessagingService`) recibe este payload directamente en segundo plano, calcula la distancia exacta al usuario, evalúa los umbrales configurados y activa la sirena y vibración incluso con la pantalla apagada.

---

## 🚀 Requisitos y Configuración

### 1. Clave de Servicio de Firebase
1. Ingresa a [Firebase Console](https://console.firebase.google.com/).
2. Selecciona tu proyecto (`sismoalerta`).
3. Ve a **Configuración del proyecto (ícono de engranaje) -> Cuentas de servicio**.
4. Haz clic en **"Generar nueva clave privada"** y descarga el archivo JSON.
5. Copia el archivo descargado en este directorio con el nombre:
   `backend/serviceAccountKey.json`
   *(Nota: Este archivo está ignorado en `.gitignore` por seguridad).*

### 2. Instalación de Dependencias
```bash
cd backend
npm install
```

---

## 🧪 Pruebas de Alerta Inmediata

Puedes enviar una alerta simulada para verificar que tu teléfono suene de inmediato con la app cerrada:

```bash
# Sintaxis: node test_send_alert.js [magnitud] [epicentro]
node test_send_alert.js 5.2 "Bucaramanga - Santander"
```

---

## 🖥️ Despliegue en Producción

### Opción A: Con PM2 en un VPS (Ubuntu / Debian)
```bash
sudo npm install -g pm2
pm2 start sgc_monitor.js --name "sismo-sgc-monitor"
pm2 save
pm2 startup
```

### Opción B: Con Docker y Docker Compose
```bash
docker compose up -d --build
docker compose logs -f
```

### Opción C: Con Cloud Functions de Firebase
Si prefieres una solución 100% serverless sin mantener un servidor VPS, puedes desplegar la función programada en la carpeta `backend/functions/`:
```bash
cd backend/functions
firebase deploy --only functions
```
