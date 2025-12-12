package com.example.organizationservice.repository;

import com.example.organizationservice.model.OrganizationMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.Optional;

public interface OrganizationMetadataRepository extends MongoRepository<OrganizationMetadata, String> {
    Optional<OrganizationMetadata> findByOrganizationName(String organizationName);
    boolean existsByOrganizationName(String organizationName);
}
