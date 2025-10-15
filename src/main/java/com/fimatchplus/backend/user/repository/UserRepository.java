package com.fimatchplus.backend.user.repository;

import com.fimatchplus.backend.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}
