package com.rit.sucy.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides pooled, asynchronous SQL access for plugin data.
 * SQLite is configured for WAL mode so readers can proceed while a writer is active.
 */
public final class ConcurrentDatabase implements AutoCloseable
{
    private final HikariDataSource dataSource;

    /**
     * Opens the default per-plugin SQLite database.
     *
     * @param plugin plugin owning the database file
     */
    public ConcurrentDatabase(Plugin plugin)
    {
        File database = new File(plugin.getDataFolder(), "mccore.db");
        if (!database.getParentFile().exists() && !database.getParentFile().mkdirs())
        {
            throw new IllegalStateException("Unable to create database directory: " + database.getParent());
        }
        HikariConfig config = new HikariConfig();
        config.setPoolName("MCCore-SQLite");
        config.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(8);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(10000);
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "10000");
        config.addDataSourceProperty("foreign_keys", "true");
        dataSource = new HikariDataSource(config);
    }

    /**
     * Runs a write operation away from the server tick thread.
     *
     * @param sql parameterized SQL statement
     * @param binder binds values to the statement
     * @return future completed with the affected row count
     */
    public CompletableFuture<Integer> execute(String sql, Consumer<PreparedStatement> binder)
    {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
            {
                if (binder != null) binder.accept(statement);
                return statement.executeUpdate();
            }
            catch (SQLException ex)
            {
                throw new CompletionException(ex);
            }
        });
    }

    /**
     * Runs a read operation with a managed connection and result set.
     *
     * @param sql parameterized SQL statement
     * @param binder binds values to the statement
     * @param reader consumes the result set before resources are closed
     * @param <T> result type
     * @return future containing the reader result
     */
    public <T> CompletableFuture<T> query(String sql, Consumer<PreparedStatement> binder, Function<ResultSet, T> reader)
    {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql))
            {
                if (binder != null) binder.accept(statement);
                try (ResultSet result = statement.executeQuery())
                {
                    return reader.apply(result);
                }
            }
            catch (SQLException ex)
            {
                throw new CompletionException(ex);
            }
        });
    }

    /**
     * Initializes a schema exactly once; callers should keep statements parameter-free.
     *
     * @param schema DDL statements separated by semicolons
     * @return future completed when the schema is ready
     */
    public CompletableFuture<Void> initialize(String schema)
    {
        return CompletableFuture.runAsync(() -> {
            try (Connection connection = dataSource.getConnection(); java.sql.Statement statement = connection.createStatement())
            {
                connection.setAutoCommit(false);
                for (String sql : schema.split(";"))
                {
                    if (!sql.trim().isEmpty()) statement.execute(sql);
                }
                connection.commit();
            }
            catch (SQLException ex)
            {
                throw new CompletionException(ex);
            }
        });
    }

    /**
     * Closes the pool and releases all SQLite handles during plugin shutdown.
     */
    @Override
    public void close()
    {
        dataSource.close();
    }
}
