import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

public class DatabaseManager {

    private static String getUrl() {
        String dbPath = System.getenv("DB_PATH");
        if (dbPath == null || dbPath.trim().isEmpty()) {
            dbPath = "water_management.db";
        } else {
            dbPath = dbPath.replace("\"", "").trim();
        }

        File file = new File(dbPath);
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }

        return "jdbc:sqlite:" + dbPath;
    }

    private static final ConcurrentHashMap<String, String> estaciones = new ConcurrentHashMap<>();

    public static void inicializarYcargarEstaciones() {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            System.err.println("Error: No se encontró el driver JDBC de SQLite.");
        }

        try (Connection conn = DriverManager.getConnection(getUrl())) {
            if (conn != null) {
                System.out.println("BD: Conectado a SQLite correctamente en (" + getUrl() + ").");
                
                String sqlCrearTabla = "CREATE TABLE IF NOT EXISTS watering_stations ("
                        + " id TEXT PRIMARY KEY,"
                        + " location TEXT NOT NULL,"
                        + " status TEXT NOT NULL"
                        + ");";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(sqlCrearTabla);
                }

                String sqlActualizarEstado = "UPDATE watering_stations SET status = 'DESCONECTADA'";
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(sqlActualizarEstado);
                }

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
                        Dashboard.cargarEstacion(id, location, Dashboard.Estado.DESCONECTADA);
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
    
    public static boolean registrarEstacion(String id, String location) {
        String sql = "INSERT INTO watering_stations (id, location, status) VALUES (?, ?, 'DISPONIBLE') " +
                     "ON CONFLICT(id) DO UPDATE SET location=excluded.location, status='DISPONIBLE'";
        try (Connection conn = DriverManager.getConnection(getUrl());
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

    public static String obtenerEstado(String id) {
        return estaciones.getOrDefault(id, "DESCONECTADA");
    }

    public static boolean actualizarEstado(String id, String estado) {
        String sql = "UPDATE watering_stations SET status = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(getUrl());
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, estado);
            pstmt.setString(2, id);
            pstmt.executeUpdate();
            
            estaciones.put(id, estado);
            return true;
        } catch (SQLException e) {
            System.err.println("Error actualizando estado de estación [" + id + "]: " + e.getMessage());
            return false;
        }
    }
}