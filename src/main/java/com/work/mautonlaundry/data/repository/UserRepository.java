package com.work.mautonlaundry.data.repository;

import com.work.mautonlaundry.data.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
