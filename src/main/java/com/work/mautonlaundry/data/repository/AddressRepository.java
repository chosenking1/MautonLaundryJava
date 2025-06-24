package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.Address;
import com.work.mautonlaundry.data.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AddressRepository extends JpaRepository<Address, Long> {
    Optional<Address> findAddressById (String id);
    List<Address> findAllByUser(User user);
    Optional<Address> findByUserAndIsDefaultTrue(User user);

    Optional<Address> findTopByUserAndDeletedFalseOrderByLastUsedDesc(User user);
    Optional<Address> findTopByUserAndDeletedFalseOrderByModifiedAtDesc(User user);
    Optional<Address> findTopByUserAndDeletedFalseOrderByCreatedAtDesc(User user);


}
