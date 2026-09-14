package com.jiabei.cloud.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CardProjectionServiceTest {
  @Test
  void formatsPendingPastAppointmentForFinalCardTemplate() {
    Map<String, Object> item = CardProjectionService.appointmentItem(
        "09:10", "欧阳小雨", "PENDING", "克里斯蒂娜", "星光直播一团", true);

    assertThat(item)
        .containsEntry("time", "09:10")
        .containsEntry("streamer", "欧阳小..")
        .containsEntry("status", "")
        .containsEntry("status_visible", false)
        .containsEntry("status_placeholder_visible", true)
        .containsEntry("makeup_artist", "克里斯蒂..")
        .containsEntry("team", "星光直播..")
        .containsEntry("row_light_color", "#A6AAB3")
        .containsEntry("row_dark_color", "#7C818B")
        .containsEntry("status_color", "gray");
  }

  @Test
  void suppliesAVisiblePlaceholderWhenThereAreNoAppointments() {
    var items = CardProjectionService.withEmptyState(java.util.List.of());

    assertThat(items).singleElement().satisfies(item -> assertThat(item)
        .containsEntry("time", "")
        .containsEntry("streamer", "暂无预约记录")
        .containsEntry("status_visible", false)
        .containsEntry("makeup_artist", "")
        .containsEntry("team", ""));
  }
  @Test
  void displaysArrivedStatusAsSignedTag() {
    Map<String, Object> item = CardProjectionService.appointmentItem(
        "08:30", "米粒", "ARRIVED", "小美老师", "晨光二团", false);

    assertThat(item)
        .containsEntry("status", "签到")
        .containsEntry("status_visible", true)
        .containsEntry("status_placeholder_visible", false)
        .containsEntry("status_color", "green");
  }
}