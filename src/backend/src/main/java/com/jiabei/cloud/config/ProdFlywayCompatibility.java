package com.jiabei.cloud.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.CRC32;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.ErrorCode;
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
    List<ValidationFailure> pending = failures.stream()
        .filter(ValidationFailure::isPending)
        .toList();
    List<ValidationFailure> blocking = failures.stream()
        .filter(failure -> !failure.isPending())
        .toList();
    boolean exactFailure = blocking.size() == 1
        && blocking.getFirst().isLegacyV10NotResolved();
    boolean exactHistory = missing.size() == 1
        && missing.getFirst().matchesLegacyV10(expectedLegacyChecksum);
    if (!exactFailure || !exactHistory) {
      throw new IllegalStateException(
          rejectionDiagnostics(failures, pending, blocking, missing, expectedLegacyChecksum));
    }
  }

  private static String rejectionDiagnostics(
      List<ValidationFailure> failures,
      List<ValidationFailure> pending,
      List<ValidationFailure> blocking,
      List<AppliedMigration> missing,
      int expectedLegacyChecksum) {
    boolean singleBlockingMigration = blocking.size() == 1;
    ValidationFailure firstFailure = blocking.isEmpty() ? null : blocking.getFirst();
    boolean legacyFailureVersion = firstFailure != null
        && LEGACY_VERSION.equals(firstFailure.version());
    boolean legacyFailureDescription = firstFailure != null
        && LEGACY_DESCRIPTION.equals(firstFailure.description());
    boolean legacyFailureErrorCode = firstFailure != null
        && firstFailure.errorCode() == ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED;
    boolean singleMissingMigration = missing.size() == 1;
    AppliedMigration firstMissing = missing.isEmpty() ? null : missing.getFirst();
    LegacyV10Match legacyMatch = LegacyV10Match.of(firstMissing, expectedLegacyChecksum);

    List<String> failedAssertions = new ArrayList<>();
    if (!singleBlockingMigration) failedAssertions.add("blockingMigrations.count");
    if (!legacyFailureVersion) failedAssertions.add("blockingMigrations.legacyVersion");
    if (!legacyFailureDescription) failedAssertions.add("blockingMigrations.legacyDescription");
    if (!legacyFailureErrorCode) failedAssertions.add("blockingMigrations.legacyErrorCode");
    if (!singleMissingMigration) failedAssertions.add("missingMigrations.count");
    legacyMatch.appendFailures(failedAssertions);

    return "Flyway validation failed; only the exact previously applied legacy V10 is compatible"
        + "; invalidMigrations.count=" + failures.size()
        + "; invalidMigrations.errorCodes=" + errorCodes(failures)
        + "; pendingMigrations.count=" + pending.size()
        + "; pendingMigrations.errorCodes=" + errorCodes(pending)
        + "; blockingMigrations.count=" + blocking.size()
        + "; blockingMigrations.errorCodes=" + errorCodes(blocking)
        + "; legacyV10.matches=" + legacyMatch
        + "; failedAssertions=" + failedAssertions;
  }

  private static Map<String, Long> errorCodes(List<ValidationFailure> failures) {
    Map<String, Long> errorCodes = new TreeMap<>();
    for (ValidationFailure failure : failures) {
      String code = failure.errorCode() == null ? "UNKNOWN" : failure.errorCode().name();
      errorCodes.merge(code, 1L, Long::sum);
    }
    return errorCodes;
  }

  private static ValidationFailure failureOf(ValidateOutput output) {
    ErrorCode errorCode = output.errorDetails == null ? null : output.errorDetails.errorCode;
    return new ValidationFailure(output.version, output.description, errorCode);
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

  record ValidationFailure(String version, String description, ErrorCode errorCode) {
    boolean isPending() {
      return errorCode == ErrorCode.RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED;
    }

    boolean isLegacyV10NotResolved() {
      return LEGACY_VERSION.equals(version)
          && LEGACY_DESCRIPTION.equals(description)
          && errorCode == ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED;
    }
  }

  record LegacyV10Match(
      boolean version,
      boolean description,
      boolean script,
      boolean type,
      boolean state,
      boolean checksum) {
    static LegacyV10Match of(AppliedMigration migration, int expectedChecksum) {
      return new LegacyV10Match(
          migration != null && LEGACY_VERSION.equals(migration.version()),
          migration != null && LEGACY_DESCRIPTION.equals(migration.description()),
          migration != null && LEGACY_SCRIPT.equals(migration.script()),
          migration != null && "SQL".equals(migration.type()),
          migration != null && migration.state() == MigrationState.MISSING_SUCCESS,
          migration != null && Objects.equals(migration.checksum(), expectedChecksum));
    }

    void appendFailures(List<String> failures) {
      if (!version) failures.add("legacyV10.version");
      if (!description) failures.add("legacyV10.description");
      if (!script) failures.add("legacyV10.script");
      if (!type) failures.add("legacyV10.type");
      if (!state) failures.add("legacyV10.state");
      if (!checksum) failures.add("legacyV10.checksum");
    }

    @Override
    public String toString() {
      return "{version=" + version
          + ", description=" + description
          + ", script=" + script
          + ", type=" + type
          + ", state=" + state
          + ", checksum=" + checksum + "}";
    }
  }

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
