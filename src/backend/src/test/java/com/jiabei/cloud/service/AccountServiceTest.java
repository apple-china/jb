package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AccountServiceTest {
  @Test void memorableSuffixesPrioritizeSimplePatternsAndRemainUnique(){
    List<String> suffixes=AccountService.memorableSuffixes();
    assertThat(suffixes).doesNotHaveDuplicates();
    assertThat(suffixes).allMatch(value->value.matches("\\d{4}"));
    assertThat(suffixes.indexOf("0000")).isLessThan(suffixes.indexOf("0011"));
    assertThat(suffixes.indexOf("0011")).isLessThan(suffixes.indexOf("0001"));
    assertThat(suffixes.indexOf("0001")).isLessThan(suffixes.indexOf("0101"));
    assertThat(suffixes.indexOf("0101")).isLessThan(suffixes.indexOf("0121"));
    assertThat(suffixes.indexOf("0121")).isLessThan(suffixes.indexOf("0123"));
  }
}
