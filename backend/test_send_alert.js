/**
 * SismoAlerta - Herramienta de Prueba de Alerta Sísmica Push FCM
 * 
 * Permite emitir una alerta sísmica de prueba de alta prioridad para verificar
 * que los dispositivos Android suenen con volumen forzado, vibren y muestren
 * la notificación Heads-Up incluso estando bloqueados o con la aplicación cerrada.
 * 
 * Uso:
 *   node test_send_alert.js [magnitud] [epicentro]
 * Ejemplo:
 *   node test_send_alert.js 5.8 "Bucaramanga - Santander"
 */

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

const serviceAccountPath = process.env.GOOGLE_APPLICATION_CREDENTIALS || path.join(__dirname, 'serviceAccountKey.json');

if (!fs.existsSync(serviceAccountPath)) {
  console.error('\n❌ ERROR: No se encontró el archivo serviceAccountKey.json en la carpeta backend/.');
  console.error('Para enviar alertas push reales a FCM necesitas descargar la clave privada de tu consola Firebase:');
  console.error('1. Ve a Firebase Console -> Configuración del proyecto -> Cuentas de servicio.');
  console.error('2. Haz clic en "Generar nueva clave privada".');
  console.error('3. Guarda el archivo descargado como: backend/serviceAccountKey.json\n');
  process.exit(1);
}

try {
  const serviceAccount = require(serviceAccountPath);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
  });
} catch (e) {
  console.error('Error al inicializar Firebase Admin:', e.message);
  process.exit(1);
}

const args = process.argv.slice(2);
const magnitude = args[0] || '5.4';
const epicenter = args[1] || 'Los Santos - Santander, Colombia';
const eventId = `TEST-SGC-${Date.now()}`;

const message = {
  topic: 'sismos_colombia',
  android: {
    priority: 'high',
    ttl: 3600
  },
  data: {
    id: eventId,
    eventId: eventId,
    magnitude: String(magnitude),
    epicenter: String(epicenter),
    latitude: '6.78',
    longitude: '-73.12',
    depthKm: '145.0',
    timestamp: String(Date.now()),
    source: 'Servicio Geológico Colombiano (Simulación de Prueba)',
    status: 'CONFIRMED'
  }
};

async function main() {
  console.log('====================================================');
  console.log('📢 DESPACHANDO ALERTA SÍSMICA DE PRUEBA A FCM');
  console.log(`🎯 Tópico: sismos_colombia`);
  console.log(`📊 Magnitud: ${magnitude} M`);
  console.log(`📍 Epicentro: ${epicenter}`);
  console.log(`🆔 ID de Evento: ${eventId}`);
  console.log('====================================================');

  try {
    const response = await admin.messaging().send(message);
    console.log(`\n✅ ¡Alerta enviada exitosamente a FCM! Message ID: ${response}`);
    console.log('👉 Todos los teléfonos suscritos recibirán la alerta en segundo plano.');
  } catch (error) {
    console.error('\n❌ Error al enviar mensaje FCM:', error);
  }
}

main();
