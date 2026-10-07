import java.sql.*;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {
    private static final String URL = "jdbc:sqlite:water_management.db";
    
    // Almacén en memoria para acceso rápido al estado de las estaciones (para luego usar en la UI/Sockets)
    private static ConcurrentHashMap<String, String> estaciones = new ConcurrentHashMap<>();

    public static void inicializarYcargarEstaciones() {
        try (Connection conn = DriverManager.getConnection(URL)) {
            if (conn != null) {
                System.out.println("BD: Conectado a SQLite correctamente.");
                
                // 1. Crear tabla si no existe
                String sqlCrearTabla = "CREATE TABLE IF NOT EXISTS watering_stations ("
                        + " id TEXT PRIMARY KEY,"
                        + " location TEXT NOT NULL,"
                        + " status TEXT NOT NULL"
                        + ");";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sqlCrearTabla);
                }

                // Subtarea 1.2.2: Marcar inicialmente todas las WS recuperadas de la BD en estado "DESCONECTADA"
                String sqlActualizarEstado = "UPDATE watering_stations SET status = 'DESCONECTADA'";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sqlActualizarEstado);
                }

                // Subtarea 1.2.1: Al iniciar/reiniciar, consultar la BD para cargar las estaciones de riego (WS)
                String sqlConsultar = "SELECT id, location, status FROM watering_stations";
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery(sqlConsultar)) {
                    
                    System.out.println("--- Estaciones de Riego Registradas ---");
                    boolean hayEstaciones = false;
                    while (rs.next()) {
                        hayEstaciones = true;
                        String id = rs.getString("id");
                        String location = rs.getString("location");
                        String status = rs.getString("status");
                        
                        estaciones.put(id, status);
                        System.out.println("ID: " + id + " | Ubicación: " + location + " | Estado: " + status);
                    }
                    if (!hayEstaciones) {
                        System.out.println("No hay estaciones registradas en la base de datos.");
                    }
                    System.out.println("---------------------------------------");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error grave de base de datos: " + e.getMessage());
        }
    }
    
    // Subtareas 2.2 y 2.3: Registrar nueva estación en BD y poner estado en DISPONIBLE
    public static boolean registrarEstacion(String id, String location) {
        String sql = "INSERT INTO watering_stations (id, location, status) VALUES (?, ?, 'DISPONIBLE') " +
                     "ON CONFLICT(id) DO UPDATE SET location=excluded.location, status='DISPONIBLE'";
        try (Connection conn = DriverManager.getConnection(URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, id);
            pstmt.setString(2, location);
            pstmt.executeUpdate();
            
            estaciones.put(id, "DISPONIBLE");
            return true;
        } catch (SQLException e) {
            System.err.println("Error registrando estación: " + e.getMessage());
            return false;
        }
    }
}
