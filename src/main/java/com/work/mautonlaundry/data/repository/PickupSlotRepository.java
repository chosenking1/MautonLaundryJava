package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.PickupSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, String> {

    /** What a customer may choose from, in the order an admin arranged. */
    List<PickupSlot> findByActiveTrueOrderBySortOrderAscStartTimeAsc();

    /** Admin listing: retired slots included, because they can be brought back. */
    List<PickupSlot> findAllByOrderBySortOrderAscStartTimeAsc();

    Optional<PickupSlot> findByLabelIgnoreCase(String label);
}
