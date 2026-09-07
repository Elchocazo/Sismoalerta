const { onSchedule } = require('firebase-functions/v2/scheduler');
const admin = require('firebase-admin');
const axios = require('axios');

if (!admin.apps.length) {
  admin.initializeApp();
}

const SGC_ENDPOINT = 'https://sismosentido.sgc.gov.co/rest/resumenSismosConIntensidadBatch/200';
const TOPIC_NAME = 'sismos_colombia';
const MIN_MAGNITUDE = 2.5;

/**
 * Cloud Function programada para ejecutarse cada 1 minuto (cron: * * * * *)
 * Consulta el SGC y despacha FCM si detecta sismos no registrados en Firestore.
 */
exports.checkSgcEarthquakes = onSchedule('every 1 minutes', async (event) => {
  const db = admin.firestore();
  const processedCol = db.collection('processed_sgc_events');

  try {
    const response = await axios.get(SGC_ENDPOINT, {
      timeout: 12000,
      headers: {
        'User-Agent': 'Mozilla/5.0 (compatible; SismoAlertaServerless/1.0; +https://github.com/Elchocazo/Sismoalerta)'
      }
    });

    if (!Array.isArray(response.data)) {
      console.log('Respuesta del SGC no es un array válido.');
      return;
    }

    const items = response.data;
    let newAlerts = 0;

    for (const item of items) {
      const rawId = item.ID_SISMO || item.ID;
      if (!rawId) continue;

      const eventId = `SGC-${rawId}`;
      const magnitude = parseFloat(item.MAGNITUD || '0');

      if (magnitude < MIN_MAGNITUDE) continue;

      // Verificar si ya fue procesado en Firestore
      const docRef = processedCol.document(eventId);
      const docSnap = await docRef.get();
      if (docSnap.exists) continue;

      const epicenter = (item.SITIO || 'Colombia').trim();
      const depthKm = parseFloat(item.PROFUNDIDAD || '10.0');
      const latitude = parseFloat(item.LATITUD || '0.0');
      const longitude = parseFloat(item.LONGITUD || '0.0');
      const timestamp = item.TIME_VALUE ? parseInt(item.TIME_VALUE, 10) * 1000 : Date.now();

      // Despachar FCM de alta prioridad
      const message = {
        topic: TOPIC_NAME,
        android: {
          priority: 'high',
          ttl: 3600
        },
        data: {
          id: eventId,
          eventId: eventId,
          magnitude: String(Math.round(magnitude * 10) / 10),
          epicenter,
          latitude: String(latitude),
          longitude: String(longitude),
          depthKm: String(Math.round(depthKm * 10) / 10),
          timestamp: String(timestamp),
          source: 'Servicio Geológico Colombiano (SGC)',
          status: 'CONFIRMED'
        }
      };

      await admin.messaging().send(message);
      newAlerts++;
      console.log(`[FCM Despachado] Sismo M ${magnitude} en ${epicenter} (ID: ${eventId})`);

      // Marcar en Firestore con TTL
      await docRef.set({
        processedAt: admin.firestore.FieldValue.serverTimestamp(),
        magnitude,
        epicenter,
        timestamp
      });
    }

    console.log(`Chequeo SGC completado. Sismos nuevos despachados: ${newAlerts}`);
  } catch (error) {
    console.error('Error al consultar SGC en Cloud Function:', error.message);
  }
});
