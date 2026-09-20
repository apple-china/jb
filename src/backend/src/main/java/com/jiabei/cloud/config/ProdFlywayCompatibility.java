package com.jiabei.cloud.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.ValidateOutput;
import org.flywaydb.core.api.output.ValidateResult;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("prod")
public class ProdFlywayCompatibility {
  private static final String LEGACY_VERSION = "10";
  private static final String LEGACY_DESCRIPTION = "reset super admin test credential";
  private static final String LEGACY_SCRIPT = "V10__reset_super_admin_test_credential.sql";
  private static final String LEGACY_RESOURCE = "db/dingtalk-test/" + LEGACY_SCRIPT;

  @Bean
  FlywayMigrationStrategy prodFlywayMigrationStrategy() {
    return ProdFlywayCompatibility::validateAndMigrate;
  }

  static void validateAndMigrate(Flyway flyway) {
    ValidateResult validation = flyway.validateWithResult();
    List<ValidationFailure> failures = validation.invalidMigrations.stream()
        .map(ProdFlywayCompatibility::failureOf)
        .toList();
    List<AppliedMigration> migrations = Arrays.stream(flyway.info().all())
        .map(ProdFlywayCompatibility::migrationOf)
        .toList();
    requireSafeValidation(validation.validationSuccessful, failures, migrations, legacyChecksum());
    flyway.migrate();
  }

  static void requireSafeValidation(
      boolean validationSuccessful,
      List<ValidationFailure> failures,
      List<AppliedMigration> migrations,
      int expectedLegacyChecksum) {
    if (validationSuccessful) return;

    List<AppliedMigration> missing = migrations.stream()
        .filter(migration -> migration.state() == MigrationState.MISSING_SUCCESS
            || migration.state() == MigrationState.MISSING_FAILED)
        .toList();
    boolean exactFailure = failures.size() == 1
        && LEGACY_VERSION.equals(failures.getFirst().version())
        && LEGACY_DESCRIPTION.equals(failures.getFirst().description());
    boolean exactHistory = missing.size() == 1
        && missing.getFirst().matchesLegacyV10(expectedLegacyChecksum);
    if (!exactFailure || !exactHistory) {
      throw new IllegalStateException(
          "Flyway validation failed; only the exact previously applied legacy V10 is compatible");
    }
  }

  private static ValidationFailure failureOf(ValidateOutput output) {
    return new ValidationFailure(output.version, output.description);
  }

  private static AppliedMigration migrationOf(MigrationInfo info) {
    return new AppliedMigration(
        info.getVersion() == null ? null : info.getVersion().getVersion(),
        info.getDescription(),
        info.getScript(),
        info.getType().toString(),
        info.getState(),
        info.getChecksum());
  }

  static int legacyChecksum() {
    CRC32 crc = new CRC32();
    ClassLoader loader = ProdFlywayCompatibility.class.getClassLoader();
    try (var stream = Objects.requireNonNull(
             loader.getResourceAsStream(LEGACY_RESOURCE), "Legacy V10 resource is missing");
         var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8), 4096)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isEmpty() && line.charAt(0) == '\uFEFF') line = line.substring(1);
        crc.update(line.getBytes(StandardCharsets.UTF_8));
      }
      return (int) crc.getValue();
    } catch (IOException error) {
      throw new IllegalStateException("Unable to calculate the immutable legacy V10 checksum", error);
    }
  }

  record ValidationFailure(String version, String description) {}

  record AppliedMigration(
      String version,
      String description,
      String script,
      String type,
      MigrationState state,
      Integer checksum) {
    boolean matchesLegacyV10(int expectedChecksum) {
      return LEGACY_VERSION.equals(version)
          && LEGACY_DESCRIPTION.equals(description)
          && LEGACY_SCRIPT.equals(script)
          && "SQL".equals(type)
          && state == MigrationState.MISSING_SUCCESS
          && Objects.equals(checksum, expectedChecksum);
    }
  }
}
