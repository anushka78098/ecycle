package com.ecycle.backend.repository;

import com.ecycle.backend.entity.*;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
 Optional<Shipment> findByOrderId(Long id);
}
