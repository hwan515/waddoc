package com.waddoc.domain.booking.repository;

import com.waddoc.domain.booking.entity.Booking;
import com.waddoc.domain.booking.entity.BookingStatus;
import com.waddoc.domain.patient.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByPublicId(String publicId);

    List<Booking> findByPatient(Patient patient);

    List<Booking> findByPatientAndStatus(Patient patient, BookingStatus status);
}
