package com.jiabei.cloud.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.flywaydb.core.api.CoreMigrationType;
import org.flywaydb.core.api.ErrorCode;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.resolver.ChecksumCalculator;
import org.junit.jupiter.api.Test;

class ProdFlywayCompatibilityTest {
  private static final int LEGACY_CHECKSUM = 123456789;

  @Test
  void acceptsAValidMigrationSetWithoutLegacyExceptions() {
    assertThatCode(() -> ProdFlywayCompatibility.requireSafeValidation(
        true, List.of(), List.of(), LEGACY_CHECKSUM)).doesNotThrowAnyException();
  }

  @Test
  void calculatesTheLegacyChecksumExactlyAsThePinnedFlywayVersion() {
    LoadableResource resource = new LoadableResource() {
      @Override public Reader read() {
        return new InputStreamReader(
            getClass().getClassLoader().getResourceAsStream(
                "db/dingtalk-test/V10__reset_super_admin_test_credential.sql"),
            StandardCharsets.UTF_8);
      }
      @Override public String getAbsolutePath() { return getRelativePath(); }
      @Override public String getAbsolutePathOnDisk() { return null; }
      @Override public String getFilename() { return "V10__reset_super_admin_test_credential.sql"; }
      @Override public String getRelativePath() { return "db/dingtalk-test/" + getFilename(); }
    };

    assertThat(ProdFlywayCompatibility.legacyChecksum())
        .isEqualTo(ChecksumCalculator.calculate(resource));
  }

  @Test
  void acceptsOnlyTheExactPreviouslyAppliedLegacyV10() {
    var failure = new ProdFlywayCompatibility.ValidationFailure(
        "10", "reset super admin test credential",
        ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED);
    var migration = legacyV10(LEGACY_CHECKSUM);

    assertThatCode(() -> ProdFlywayCompatibility.requireSafeValidation(
        false, List.of(failure), List.of(migration), LEGACY_CHECKSUM))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsLegacyV10WhenItsChecksumDoesNotMatchTheImmutableScript() {
    assertThatThrownBy(() -> ProdFlywayCompatibility.requireSafeValidation(
        false,
        List.of(new ProdFlywayCompatibility.ValidationFailure(
            "10", "reset super admin test credential",
            ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED)),
        List.of(legacyV10(LEGACY_CHECKSUM + 1)),
        LEGACY_CHECKSUM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalidMigrations.count=1")
        .hasMessageContaining(
            "invalidMigrations.errorCodes={APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED=1}")
        .hasMessageContaining("checksum=false")
        .hasMessageContaining("failedAssertions=[legacyV10.checksum]");
  }

  @Test
  void rejectsAnyAdditionalMissingMigration() {
    var legacy = legacyV10(LEGACY_CHECKSUM);
    var other = new ProdFlywayCompatibility.AppliedMigration(
        "7", "unexpected migration", "V7__unexpected_migration.sql",
        CoreMigrationType.SQL.toString(), MigrationState.MISSING_SUCCESS, 7);

    assertThatThrownBy(() -> ProdFlywayCompatibility.requireSafeValidation(
        false,
        List.of(
            new ProdFlywayCompatibility.ValidationFailure(
                "10", "reset super admin test credential",
                ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED),
            new ProdFlywayCompatibility.ValidationFailure(
                "7", "unexpected migration", ErrorCode.CHECKSUM_MISMATCH)),
        List.of(legacy, other),
        LEGACY_CHECKSUM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("invalidMigrations.count=2")
        .hasMessageContaining(
            "invalidMigrations.errorCodes={APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED=1, CHECKSUM_MISMATCH=1}")
        .hasMessageContaining(
            "legacyV10.matches={version=true, description=true, script=true, type=true, state=true, checksum=true}")
        .hasMessageContaining(
            "failedAssertions=[invalidMigrations.count, missingMigrations.count]");
  }

  @Test
  void rejectsAFailedOrPendingV10InsteadOfTreatingItAsLegacyHistory() {
    var failed = new ProdFlywayCompatibility.AppliedMigration(
        "10", "reset super admin test credential",
        "V10__reset_super_admin_test_credential.sql",
        CoreMigrationType.SQL.toString(), MigrationState.MISSING_FAILED, LEGACY_CHECKSUM);

    assertThatThrownBy(() -> ProdFlywayCompatibility.requireSafeValidation(
        false,
        List.of(new ProdFlywayCompatibility.ValidationFailure(
            "10", "reset super admin test credential",
            ErrorCode.APPLIED_VERSIONED_MIGRATION_NOT_RESOLVED)),
        List.of(failed),
        LEGACY_CHECKSUM))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("state=false")
        .hasMessageContaining("failedAssertions=[legacyV10.state]");
  }

  private static ProdFlywayCompatibility.AppliedMigration legacyV10(int checksum) {
    return new ProdFlywayCompatibility.AppliedMigration(
        "10", "reset super admin test credential",
        "V10__reset_super_admin_test_credential.sql",
        CoreMigrationType.SQL.toString(), MigrationState.MISSING_SUCCESS, checksum);
  }
}
