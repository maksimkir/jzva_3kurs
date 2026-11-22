package com.example.library.external;

import com.example.library.common.bookinfo.AvailabilityInfo;
import com.example.library.common.bookinfo.RatingInfo;
import com.example.library.dto.BookDto;

/** ЛР7 — синхронний доступ до book-info-service. Інтерфейс — щоб тести могли підмінити HTTP керованою затримкою. */
public interface BookInfoApi {

    RatingInfo rating(BookDto book);

    AvailabilityInfo availability(BookDto book);
}
