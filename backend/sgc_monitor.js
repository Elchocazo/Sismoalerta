/**
 * SismoAlerta - SGC Real-time Earthquake Monitor & FCM Push Dispatcher
 * 
 * Monitorea el endpoint oficial del Servicio Geológico Colombiano (SGC):
 * https://sismosentido.sgc.gov.co/rest/resumenSismosConIntensidadBatch/200
 * 
 * Detecta nuevos eventos sísmicos en tiempo real y despacha inmediatamente un mensaje
 * FCM de ALTA PRIORIDAD (Data-Only Payload) al tópico 'sismos_colombia'.
 * 
 * Esto despierta los teléfonos Android de los usuarios aunque la app esté cerrada,
 * en reposo Doze, con la pantalla apagada o el proceso detenido.
 */

const fs = require('fs');
const path = require('path');
const axios = require('axios');
const admin = require('firebase-admin');

// Configuración
const SGC_ENDPOINT = process.env.SGC_ENDPOINT || 'https://sismosentido.sgc.gov.co/rest/resumenSismosConIntensidadBatch/200';
const POLL_INTERVAL_MS = parseInt(process.env.POLL_INTERVAL_MS || '30000', 10); // 30 segundos
const MIN_MAGNITUDE = parseFloat(process.env.MIN_MAGNITUDE || '2.0');
const TOPIC_NAME = 'sismos_colombia';
const CACHE_FILE = path.join(__dirname, 'known_sismos.json');

// Inicializar Firebase Admin SDK
let firebaseApp = null;
const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

if (fs.existsSync(serviceAccountPath)) {
  try {
    const serviceAccount = require(serviceAccountPath);
    firebaseApp = admin.initializeApp({
      credential: admin.credential.cert(serviceAccount)
    });
    console.log('[Firebase] Admin SDK inicializado correctamente con credenciales oficiales ✓');
  } catch (err) {
    console.error('[Firebase] Error al cargar serviceAccountKey.json:', err.message);
  }
} else {
  console.warn('[Firebase] ADVERTENCIA: No se encontró serviceAccountKey.json ni variable GOOGLE_APPLICATION_CREDENTIALS.');
  console.warn('[Firebase] El monitor funcionará en MODO OBSERVADOR / DRY-RUN (detecta y loguea sismos del SGC sin enviar FCM).');
}

// Cargar caché local de sismos ya procesados
let knownEventIds = new Set();
if (fs.existsSync(CACHE_FILE)) {
  try {
    const saved = JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8'));
    if (Array.isArray(saved)) {
      knownEventIds = new Set(saved);
      console.log(`[Caché] Cargados ${knownEventIds.size} eventos previos para evitar falsos positivos al arrancar.`);
    }
  } catch (e) {
    console.warn('[Caché] No se pudo leer el archivo de caché previo:', e.message);
  }
}

function saveCache() {
  try {
    // Guardar los últimos 500 IDs para mantener el archivo ligero
    const idArray = Array.from(knownEventIds).slice(-500);
    fs.writeFileSync(CACHE_FILE, JSON.stringify(idArray, null, 2), 'utf8');
  } catch (e) {
    console.error('[Caché] Error al guardar caché:', e.message);
  }
}

/**
 * Envía la alerta push de alta prioridad a través de Firebase Cloud Messaging.
 */
async function sendFcmEarthquakeAlert(event) {
  if (!firebaseApp) {
    console.log(`[DRY-RUN] [FCM Simulado] Enviando sismo M ${event.magnitude} en ${event.epicenter} (ID: ${event.id})`);
    return;
  }

  // IMPORTANTE: En FCM todos los valores del objeto 'data' deben ser cadenas de texto (Strings)
  const message = {
    topic: TOPIC_NAME,
    android: {
      priority: 'high',
      ttl: 60 * 60 // 1 hora de vigencia máxima para sismos recientes
    },
    data: {
      id: String(event.id),
      eventId: String(event.id),
      magnitude: String(event.magnitude),
      epicenter: String(event.epicenter),
      latitude: String(event.latitude),
      longitude: String(event.longitude),
      depthKm: String(event.depthKm),
      timestamp: String(event.timestamp),
      source: 'Servicio Geológico Colombiano (SGC)',
      status: 'CONFIRMED'
    }
  };

  try {
    const response = await admin.messaging().send(message);
    console.log(`[FCM] ¡Alerta enviada exitosamente al tópico '${TOPIC_NAME}'! Message ID: ${response}`);
    console.log(`       Detalles: M ${event.magnitude} - ${event.epicenter} (Prof: ${event.depthKm} km, Lat: ${event.latitude}, Lon: ${event.longitude})`);
  } catch (error) {
    console.error(`[FCM] Error al enviar alerta push para evento ${event.id}:`, error.message);
  }
}

/**
 * Consulta el endpoint del SGC y procesa sismos nuevos.
 */
let isFirstRun = knownEventIds.size === 0;

async function checkSgcFeed() {
  const timestampStr = new Date().toISOString();
  try {
    const response = await axios.get(SGC_ENDPOINT, {
      timeout: 15000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; SismoAlertaMonitor/1.0; +https://github.com/Elchocazo/Sismoalerta)',
        'Accept': 'application/json, text/plain, */*'
      }
    });

    if (!Array.isArray(response.data)) {
      console.warn(`[${timestampStr}] [SGC] Respuesta no es un arreglo válido. Status: ${response.status}`);
      return;
    }

    const items = response.data;
    let newEventsCount = 0;

    // Recorrer los sismos (el endpoint suele retornar los más recientes primero o por lotes)
    for (const item of items) {
      const rawId = item.ID_SISMO || item.ID || null;
      if (!rawId) continue;

      const eventId = `SGC-${rawId}`;
      const magnitude = parseFloat(item.MAGNITUD || '0');
      const epicenter = (item.SITIO || 'Colombia').trim();
      const depthKm = parseFloat(item.PROFUNDIDAD || '10.0');
      const latitude = parseFloat(item.LATITUD || '0.0');
      const longitude = parseFloat(item.LONGITUD || '0.0');

      let timestamp = 0;
      if (item.TIME_VALUE && parseInt(item.TIME_VALUE, 10) > 0) {
        timestamp = parseInt(item.TIME_VALUE, 10) * 1000;
      } else {
        timestamp = Date.now();
      }

      // Si es la primera ejecución y el caché estaba vacío, indexamos el historial sin alertar
      if (isFirstRun) {
        knownEventIds.add(eventId);
        continue;
      }

      // Si ya fue procesado, omitir
      if (knownEventIds.has(eventId)) {
        continue;
      }

      // Registrar nuevo evento
      knownEventIds.add(eventId);
      newEventsCount++;

      // Verificar umbral mínimo de magnitud del backend
      if (magnitude < MIN_MAGNITUDE) {
        console.log(`[${timestampStr}] [SGC] Sismo menor detectado M ${magnitude} (${epicenter}). Filtrado por umbral (< ${MIN_MAGNITUDE}).`);
        continue;
      }

      // Despachar alerta inmediata
      console.log(`\n=============================================================`);
      console.log(`🚨 [${timestampStr}] ¡NUEVO SISMO REGISTRADO EN COLOMBIA!`);
      console.log(`   ID: ${eventId}`);
      console.log(`   Magnitud: ${magnitude} M`);
      console.log(`   Epicentro: ${epicenter}`);
      console.log(`   Profundidad: ${depthKm} km | Coordenadas: (${latitude}, ${longitude})`);
      console.log(`=============================================================\n`);

      const event = {
        id: eventId,
        magnitude: Math.round(magnitude * 10) / 10,
        epicenter,
        depthKm: Math.round(depthKm * 10) / 10,
        latitude,
        longitude,
        timestamp
      };

      await sendFcmEarthquakeAlert(event);
    }

    if (isFirstRun) {
      console.log(`[${timestampStr}] [SGC] Inicialización completa: ${knownEventIds.size} eventos históricos indexados en caché.`);
      saveCache();
      isFirstRun = false;
    } else if (newEventsCount > 0) {
      saveCache();
    } else {
      process.stdout.write(`[${timestampStr}] Monitoreo SGC activo (0 sismos nuevos). Próximo chequeo en ${POLL_INTERVAL_MS / 1000}s...\r`);
    }

  } catch (err) {
    console.error(`[${timestampStr}] [SGC Error] Falló la consulta al endpoint: ${err.message}`);
  }
}

// Bucle continuo de monitoreo
console.log('------------------------------------------------------------');
console.log('🚀 Iniciando Servicio de Monitoreo SGC - SismoAlerta v1.0');
console.log(`📡 Endpoint: ${SGC_ENDPOINT}`);
console.log(`⏱️  Frecuencia de muestreo: cada ${POLL_INTERVAL_MS / 1000} segundos`);
console.log(`🎯 Tópico de destino FCM: ${TOPIC_NAME}`);
console.log(`📊 Magnitud mínima a despachar: >= ${MIN_MAGNITUDE}`);
console.log('------------------------------------------------------------');

// Primer chequeo inmediato
checkSgcFeed();

// Programar intervalo
const intervalId = setInterval(checkSgcFeed, POLL_INTERVAL_MS);

// Captura de terminación limpia
process.on('SIGINT', () => {
  console.log('\nDeteniendo monitor SGC...');
  clearInterval(intervalId);
  saveCache();
  process.exit(0);
});

process.on('SIGTERM', () => {
  console.log('\nDeteniendo monitor SGC (SIGTERM)...');
  clearInterval(intervalId);
  saveCache();
  process.exit(0);
});
