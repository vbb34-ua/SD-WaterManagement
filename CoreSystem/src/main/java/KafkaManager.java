import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

/**
 * KafkaManager — Gestiona el productor Kafka de WM_Central.
 *
 * WM_Central actúa como productor: publica eventos del sistema
 * (riegos, averías, aborts...) en topics que el Engine (WM_WS_E)
 * consume como suscriptor.
 *
 * Topic principal: "wm-eventos"
 * Formato de los mensajes: clave = tipo de evento, valor = ID de la WS
 */
public class KafkaManager {

    private static KafkaProducer<String, String> productor;
    private static final String TOPIC_EVENTOS = "wm-eventos";
    private static boolean inicializado = false;

    /**
     * Inicializa el productor Kafka.
     * Debe llamarse una vez al arrancar WM_Central, después de arrancar el Dashboard.
     *
     * @param brokerUrl URL del broker, ej: "localhost:9092" o "broker:29092"
     */
    public static void inicializar(String brokerUrl) {
        try {
            Properties props = new Properties();
            props.put("bootstrap.servers", brokerUrl);
            props.put("key.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer",
                    "org.apache.kafka.common.serialization.StringSerializer");
            // Tiempo máximo de bloqueo si el broker no responde (evita colgarse al arrancar)
            props.put("max.block.ms", "5000");
            // Reintentos automáticos si el broker falla puntualmente
            props.put("retries", "3");

            productor = new KafkaProducer<>(props);
            inicializado = true;
            Dashboard.log("[KAFKA] Productor conectado a " + brokerUrl);
        } catch (Exception e) {
            System.err.println("[KAFKA] Error al conectar con el broker (" + brokerUrl + "): " + e.getMessage());
            System.err.println("[KAFKA] La Central seguirá funcionando SIN Kafka.");
        }
    }

    /**
     * Publica un evento en el topic principal de WaterManagement.
     *
     * @param tipoEvento Clave del mensaje (ej: "ABORTO_RIEGO", "INICIO_RIEGO")
     * @param idWS       Valor del mensaje — el ID de la WS afectada
     */
    public static void publicarEvento(String tipoEvento, String idWS) {
        if (!inicializado || productor == null) {
            System.err.println("[KAFKA] Evento no publicado (Kafka no disponible): " + tipoEvento + " / " + idWS);
            return;
        }
        try {
            ProducerRecord<String, String> record =
                    new ProducerRecord<>(TOPIC_EVENTOS, tipoEvento, idWS);

            productor.send(record, (metadata, ex) -> {
                if (ex != null) {
                    System.err.println("[KAFKA] Error publicando evento '" + tipoEvento + "': " + ex.getMessage());
                } else {
                    System.out.println("[KAFKA] Evento publicado: " + tipoEvento +
                            " -> WS:" + idWS +
                            " (partition=" + metadata.partition() +
                            ", offset=" + metadata.offset() + ")");
                }
            });
        } catch (Exception e) {
            System.err.println("[KAFKA] Excepción al publicar evento: " + e.getMessage());
        }
    }

    /**
     * Cierra el productor de forma limpia.
     * Llamar al apagar WM_Central (shutdown hook).
     */
    public static void cerrar() {
        if (productor != null) {
            productor.close();
            inicializado = false;
            System.out.println("[KAFKA] Productor cerrado.");
        }
    }

    public static boolean isInicializado() {
        return inicializado;
    }
}
