package com.example.bookinfo;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Unit-тести без Spring: затримка 0, відповіді детерміновані. */
class BookInfoServiceTest {

    private final BookInfoService service = new BookInfoService(new LatencySimulator(0));

    @Test
    void rating_validIsbn_returnsAvailableRatingInRange() {
        RatingInfo rating = service.rating("9780132350884");

        assertThat(rating.available()).isTrue();
        assertThat(rating.average()).isBetween(3.0, 5.0);
        assertThat(rating.votes()).isBetween(10, 999);
    }

    @Test
    void rating_sameIsbn_isDeterministic() {
        assertThat(service.rating("9780134685991")).isEqualTo(service.rating("9780134685991"));
    }

    @Test
    void rating_missingOrBlankIsbn_throwsWithExplanation() {
        assertThatThrownBy(() -> service.rating(null)).isInstanceOf(MissingIsbnException.class).hasMessageContaining("ISBN");
        assertThatThrownBy(() -> service.rating("  ")).isInstanceOf(MissingIsbnException.class);
    }

    @Test
    void availability_returnsConsistentCopies() {
        AvailabilityInfo info = service.availability("Clean Code", "Robert C. Martin");

        assertThat(info.available()).isTrue();
        assertThat(info.partnerLibraries()).isBetween(0, 5);
        if (info.partnerLibraries() == 0) {
            assertThat(info.copies()).isZero();
        } else {
            assertThat(info.copies()).isGreaterThanOrEqualTo(info.partnerLibraries());
        }
    }

    @Test
    void latencySimulator_positiveDelay_completes() {
        new LatencySimulator(1).simulate();
    }
}
