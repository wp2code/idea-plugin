package com.lsy.idea.db;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.jetbrains.annotations.NotNull;

/**
 * SQLite 数据库连接管理器（项目级服务） 负责数据库文件初始化、建表、灌入初始数据 数据库路径：{project.basePath}/.idea/permission-sql/db.sqlite
 */
public class DatabaseManager implements Disposable {
    
    private static final Logger LOG = Logger.getInstance(DatabaseManager.class);
    
    /**
     * 默认模板 SQL 模板内容（使用新变量名体系）
     */
    private static final String DEFAULT_TEMPLATE_CONTENT = """
            INSERT INTO "dunwu_auth"."resource_info" ( resource_code_value, resource_name, view_name_key, resource_code, type, permission_type, remark, create_time, update_time, creator_id, updater_id, deleted )
            VALUES ( '${resourceAddress}', '${resourceName}', '${i18nResourceCode}', '${resourceCode}','${resourceType}', '${permissionType}', '${remark}', NOW(), NOW(), '${creatorId}', '${updaterId}', 0 )
            """;
    
    private Connection connection;
    
    private final String dbPath;
    
    public DatabaseManager(@NotNull Project project) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            basePath = System.getProperty("user.home");
        }
        this.dbPath = basePath + File.separator + ".idea" + File.separator + "permission-sql" + File.separator + "db.sqlite";
    }
    
    /**
     * 获取当前项目的 DatabaseManager 实例
     */
    public static DatabaseManager getInstance(@NotNull Project project) {
        return project.getService(DatabaseManager.class);
    }
    
    /**
     * 初始化数据库：加载驱动、建表、灌入初始数据
     */
    public void initDatabase() {
        try {
            // 1. 确保目录存在
            File dbFile = new File(dbPath);
            File parentDir = dbFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // 2. 加载 SQLite 驱动
            Class.forName("org.sqlite.JDBC");
            
            // 3. 建立连接
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            connection.setAutoCommit(true);
            
            // 4. 启用外键
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA foreign_keys = ON");
                stmt.execute("PRAGMA journal_mode = WAL");
            }
            
            // 5. 建表
            createTables();
            
            // 6. 灌入初始数据（仅当表为空时）
            seedInitialData();
            
            LOG.info("Api2Sql: database initialized at " + dbPath);
        } catch (ClassNotFoundException e) {
            LOG.error("Api2Sql: SQLite JDBC driver not found", e);
        } catch (SQLException e) {
            LOG.error("Api2Sql: Failed to initialize database: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取数据库连接（供 DAO 使用）
     */
    public Connection getConnection() {
        return connection;
    }
    
    /**
     * 建三张表（IF NOT EXISTS）
     */
    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // 资源类型字典表
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS dict_resource_type (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            type_code INTEGER NOT NULL UNIQUE,
                            type_name TEXT NOT NULL,
                            sort INTEGER NOT NULL DEFAULT 0
                        )
                    """);
            
            // 权限类型字典表
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS dict_permission_type (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            type_code INTEGER NOT NULL UNIQUE,
                            type_name TEXT NOT NULL,
                            sort INTEGER NOT NULL DEFAULT 0
                        )
                    """);
            
            // SQL 脚本模板表
            stmt.execute("""
                        CREATE TABLE IF NOT EXISTS script_template (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            template_name TEXT NOT NULL UNIQUE,
                            template_content TEXT NOT NULL,
                            creator_id_value TEXT DEFAULT '',
                            updater_id_value TEXT DEFAULT '',
                            http_code_api TEXT DEFAULT '',
                            default_code_mode INTEGER NOT NULL DEFAULT 0,
                            ext_config TEXT DEFAULT '{}',
                            http_headers TEXT DEFAULT '{}',
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                        )
                    """);
            
            // 小版本迁移：添加 http_headers 列（若已存在则忽略）
            migrateAddHttpHeaders(stmt);
        }
    }
    
    /**
     * 灌入初始字典数据和默认模板（仅当表为空时）
     */
    private void seedInitialData() throws SQLException {
        seedResourceTypes();
        seedPermissionTypes();
        seedDefaultTemplate();
    }
    
    private void seedResourceTypes() throws SQLException {
        var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM dict_resource_type");
        if (count.next() && count.getInt(1) > 0) {
            return;
        }
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO dict_resource_type(type_code,type_name,sort) VALUES (1,'接口',1)");
        }
    }
    
    private void seedPermissionTypes() throws SQLException {
        var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM dict_permission_type");
        if (count.next() && count.getInt(1) > 0) {
            return;
        }
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (0,'查看',0)");
            stmt.execute("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (1,'新增',1)");
            stmt.execute("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (2,'编辑',2)");
            stmt.execute("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (3,'删除',3)");
            stmt.execute("INSERT INTO dict_permission_type(type_code,type_name,sort) VALUES (4,'控制',4)");
        }
    }
    
    private void seedDefaultTemplate() throws SQLException {
        var count = connection.createStatement().executeQuery("SELECT COUNT(*) FROM script_template");
        if (count.next() && count.getInt(1) > 0) {
            return;
        }
        
        try (var pstmt = connection.prepareStatement(
                "INSERT INTO script_template(template_name,template_content,creator_id_value,updater_id_value,default_code_mode,ext_config,http_headers) VALUES (?,?,?,?,?,?,?)")) {
            pstmt.setString(1, "默认权限模板");
            pstmt.setString(2, DEFAULT_TEMPLATE_CONTENT);
            pstmt.setString(3, "1");
            pstmt.setString(4, "1");
            pstmt.setInt(5, 0);
            pstmt.setString(6, "{}");
            pstmt.setString(7, "{}");
            pstmt.executeUpdate();
        }
    }
    
    /**
     * 小版本迁移：尝试添加 http_headers 列，如果已存在则忽略异常
     */
    private void migrateAddHttpHeaders(Statement stmt) {
        try {
            stmt.execute("ALTER TABLE script_template ADD COLUMN http_headers TEXT DEFAULT '{}'");
        } catch (SQLException ignored) {
            // 列已存在，忽略即可
        }
    }
    
    @Override
    public void dispose() {
        if (connection != null) {
            try {
                connection.close();
                LOG.info("Api2Sql: database connection closed");
            } catch (SQLException e) {
                LOG.warn("Error closing database connection", e);
            }
        }
    }
}
