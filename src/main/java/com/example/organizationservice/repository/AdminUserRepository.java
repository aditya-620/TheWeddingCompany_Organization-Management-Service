package com.example.organizationservice.repository;

import com.example.organizationservice.model.AdminUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface AdminUserRepository extends MongoRepository<AdminUser, String> {
    Optional<AdminUser> findByEmail(String email);
    Optional<AdminUser> findByEmailAndOrganizationName(String email, String organizationName);
    Optional<AdminUser> findByOrganizationName(String organizationName);
}
