package com.hari.bookingservice.booking;

import com.hari.bookingservice.booking.dto.BookingResponse;
import com.hari.bookingservice.booking.exceptions.BookingNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final BookingItemRepository bookingItemRepository;

    public BookingService(BookingRepository bookingRepository,
                          BookingItemRepository bookingItemRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingItemRepository = bookingItemRepository;
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        return BookingResponse.from(booking, bookingItemRepository.findByBookingId(bookingId));
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listByUser(UUID userId) {
        return bookingRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(b -> BookingResponse.from(b, bookingItemRepository.findByBookingId(b.getId())))
                .toList();
    }
}