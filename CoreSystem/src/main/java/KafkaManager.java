import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaManager {

    private static KafkaProducer<String, String> productor;
    private static final String TOPIC_EVENTOS = "wm-eventos";
    private static boolean inicializado = false;

    public static void inicializar(String brokerUrl) {
        try {
            Properties props = new Properties();
            props.put("bootstrap.servers", brokerUrl);
            props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
            props.put("max.block.ms", "5000");
            props.put("retries", "3");

            productor = new KafkaProducer<>(props);
            inicializado = true;
            Dashboard.log("[KAFKA] Productor conectado a " + brokerUrl);
        } catch (Exception e) {
            System.err.println("[KAFKA] Error al conectar con el broker (" + brokerUrl + "): " + e.getMessage());
            System.err.println("[KAFKA] La Central seguirá funcionando SIN Kafka.");
        }
    }

    public static void publicarEvento(String tipoEvento, String payload) {
        if (!inicializado || productor == null) {
            System.err.println("[KAFKA] Evento no publicado (Kafka no disponible): " + tipoEvento + " / " + payload);
            return;
        }
        try {
            // Extraer el idWS del payload (ej: "WS_01#OP_01" -> "WS_01")
            String idWS = payload.contains("#") ? payload.split("#")[0] : payload;
            String mensaje = tipoEvento + "#" + payload;

            // Clave = idWS para garantizar ordenación por partición en Kafka
            ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC_EVENTOS, idWS, mensaje);

            productor.send(record, (metadata, ex) -> {
                if (ex != null) {
                    System.err.println("[KAFKA] Error publicando evento '" + tipoEvento + "': " + ex.getMessage());
                } else {
                    System.out.println("[KAFKA] Evento publicado (Key=" + idWS + "): " + mensaje +
                            " (partition=" + metadata.partition() + ", offset=" + metadata.offset() + ")");
                }
            });
        } catch (Exception e) {
            System.err.println("[KAFKA] Excepción al publicar evento: " + e.getMessage());
        }
    }

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