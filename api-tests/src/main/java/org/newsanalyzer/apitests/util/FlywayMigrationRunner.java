package org.newsanalyzer.apitests.util;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.CleanResult;
import org.flywaydb.core.api.output.MigrateResult;

import javax.sql.DataSource;

/**
 * Flyway migration runner for API integration tests.
 * Points to backend's migration scripts and provides clean + migrate functionality.
 */
public class FlywayMigrationRunner {

    // Path to backend migration scripts (relative to project root)
    private static final String DEFAULT_MIGRATION_LOCATION = "filesystem:../backend/src/main/resources/db/migration";

    private final Flyway flyway;
    @SuppressWarnings("unused")
    private final DataSource dataSource;

    /**
     * Create migration runner with default migration location.
     */
    public FlywayMigrationRunner(DataSource dataSource) {
        this(dataSource, DEFAULT_MIGRATION_LOCATION);
    }

    /**
     * Create migration runner with custom migration location.
     */
    public FlywayMigrationRunner(DataSource dataSource, String migrationLocation) {
        this.dataSource = dataSource;
        this.flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(migrationLocation)
                .cleanDisabled(false)  // Enable clean for tests
                .baselineOnMigrate(true)  // Baseline if needed
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }

    /**
     * Run all pending migrations.
     * @return MigrateResult with migration details
     */
    public MigrateResult migrate() {
        System.out.println("Running Flyway migrations...");
        MigrateResult result = flyway.migrate();
        System.out.printf("Migrations applied: %d, Schema version: %s%n",
                result.migrationsExecuted,
                result.targetSchemaVersion);
        return result;
    }

    /**
     * Clean the database (drop all objects) and then migrate.
     * Use this for a fresh database state.
     * @return MigrateResult after clean and migrate
     */
    public MigrateResult cleanAndMigrate() {
        System.out.println("Cleaning database...");
        CleanResult cleanResult = flyway.clean();
        System.out.printf("Cleaned %d schemas%n", cleanResult.schemasCleaned.size());

        return migrate();
    }

    /**
     * Clean the database (drop all objects).
     * WARNING: This will delete all data!
     * @return CleanResult with details
     */
    public CleanResult clean() {
        System.out.println("Cleaning database...");
        return flyway.clean();
    }

    /**
     * Validate migrations without applying them.
     * Checks if migrations are valid and consistent.
     */
    public void validate() {
        System.out.println("Validating migrations...");
        flyway.validate();
        System.out.println("Migrations valid");
    }

    /**
     * Get information about all migrations.
     * @return MigrationInfoService with migration details
     */
    public MigrationInfoService info() {
        return flyway.info();
    }

    /**
     * Print migration status to console.
     */
    public void printMigrationStatus() {
        MigrationInfoService info = flyway.info();
        MigrationInfo[] all = info.all();

        System.out.println("\n=== Migration Status ===");
        System.out.printf("Current version: %s%n",
                info.current() != null ? info.current().getVersion() : "none");
        System.out.printf("Total migrations: %d%n", all.length);
        System.out.printf("Pending migrations: %d%n", info.pending().length);

        System.out.println("\nMigration history:");
        for (MigrationInfo migration : all) {
            System.out.printf("  %s | %s | %s | %s%n",
                    migration.getVersion(),
                    migration.getDescription(),
                    migration.getState(),
                    migration.getInstalledOn() != null ? migration.getInstalledOn() : "not applied");
        }
        System.out.println("========================\n");
    }

    /**
     * Check if any migrations are pending.
     * @return true if there are pending migrations
     */
    public boolean hasPendingMigrations() {
        return flyway.info().pending().length > 0;
    }

    /**
     * Get the current schema version.
     * @return current version string or null if no migrations applied
     */
    public String getCurrentVersion() {
        MigrationInfo current = flyway.info().current();
        return current != null ? current.getVersion().toString() : null;
    }

    /**
     * Baseline the database at the current state.
     * Use when starting from an existing schema.
     */
    public void baseline() {
        System.out.println("Baselining database...");
        flyway.baseline();
    }

    /**
     * Repair the schema history table.
     * Removes failed migration entries.
     */
    public void repair() {
        System.out.println("Repairing migration history...");
        flyway.repair();
    }

    /**
     * Get the underlying Flyway instance for advanced configuration.
     */
    public Flyway getFlyway() {
        return flyway;
    }

    /**
     * Static factory method using DatabaseConnectionManager.
     */
    public static FlywayMigrationRunner create() {
        DataSource dataSource = DatabaseConnectionManager.getInstance().getDataSource();
        return new FlywayMigrationRunner(dataSource);
    }

    /**
     * Static method to run migrations using default settings.
     */
    public static MigrateResult runMigrations() {
        return create().migrate();
    }

    /**
     * Static method to clean and migrate using default settings.
     */
    public static MigrateResult cleanAndRunMigrations() {
        return create().cleanAndMigrate();
    }
}
