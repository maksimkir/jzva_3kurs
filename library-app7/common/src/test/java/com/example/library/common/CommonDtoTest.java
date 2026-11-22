package com.example.library.common;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.common.notification.BookCreatedNotification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Контракти між сервісами: фабричні методи і семантика значень (record equals). */
class CommonDtoTest {

    @Test
    void ratingInfo_of_isAvailableWithValues() {
        RatingInfo rating = RatingInfo.of(4.5, 120);

        assertThat(rating.available()).isTrue();
        assertThat(rating.average()).isEqualTo(4.5);
        assertThat(rating.votes()).isEqualTo(120);
        assertThat(rating.error()).isNull();
    }

    @Test
    void ratingInfo_unavailable_carriesReasonWithoutValues() {
        RatingInfo rating = RatingInfo.unavailable("тайм-аут");

        assertThat(rating.available()).isFalse();
        assertThat(rating.average()).isNull();
        assertThat(rating.error()).isEqualTo("тайм-аут");
    }

    @Test
    void availabilityInfo_factories_setFlagsAndValues() {
        assertThat(AvailabilityInfo.of(3, 7)).isEqualTo(new AvailabilityInfo(true, 3, 7, null));
        assertThat(AvailabilityInfo.unavailable("catalog down").available()).isFalse();
        assertThat(AvailabilityInfo.unavailable("catalog down").error()).isEqualTo("catalog down");
    }

    @Test
    void bookCreatedNotification_isValueObject() {
        assertThat(new BookCreatedNotification(1L, "Clean Code", "Robert C. Martin"))
                .isEqualTo(new BookCreatedNotification(1L, "Clean Code", "Robert C. Martin"))
                .extracting(BookCreatedNotification::bookId).isEqualTo(1L);
    }
}
