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
  void buildsAStableWarmLateReminderFromTheApprovedPools() {
    var appointmentId = new java.util.UUID(0, 0);

    assertThat(CardProjectionService.lateReminderText(appointmentId, "玲玲", "user-1"))
        .isEqualTo("<a atId=user-1>玲玲</a> 姐姐，化妆老师在等你呢～请尽快到司签到 ✨");
    assertThat(CardProjectionService.lateReminderText(appointmentId, "玲玲", "user-1"))
        .isEqualTo(CardProjectionService.lateReminderText(appointmentId, "玲玲", "user-1"));
  }
  @Test
  void excludesThePreviousFiveTitlesAndMessagesAndUsesOneOrTwoEmojis() {
    var recent = java.util.List.of(0, 1, 2, 3, 4);

    var oneEmoji = CardProjectionService.selectLateReminder(
        new java.util.UUID(0, 0), "玲玲", "user-1", recent, recent);
    assertThat(oneEmoji.titleIndex()).isEqualTo(5);
    assertThat(oneEmoji.messageIndex()).isEqualTo(5);
    assertThat(oneEmoji.text())
        .isEqualTo("<a atId=user-1>玲玲</a> 宝儿，化妆预约已经开始啦～别让老师等太久哈 ✨");

    var twoEmojis = CardProjectionService.selectLateReminder(
        new java.util.UUID(0, 1), "玲玲", "user-1", recent, recent);
    assertThat(twoEmojis.emojiCount()).isEqualTo(2);
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