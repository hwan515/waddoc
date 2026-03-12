package com.waddoc.domain.carecase.repository;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.carecase.entity.CareCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CareCaseRepository extends JpaRepository<CareCase, Long> {

    Optional<CareCase> findByPublicId(String publicId);

    Optional<CareCase> findByBooking(Booking booking);
}
