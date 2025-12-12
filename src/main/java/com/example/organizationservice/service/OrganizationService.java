package com.example.organizationservice.service;

import com.example.organizationservice.model.OrganizationMetadata;
import com.example.organizationservice.model.AdminUser;
import com.example.organizationservice.repository.AdminUserRepository;
import com.example.organizationservice.repository.OrganizationMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;
import java.util.Optional;

@Service
public class OrganizationService {

    @Autowired
    private OrganizationMetadataRepository orgRepo;

    @Autowired
    private AdminUserRepository adminRepo;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    private String sanitizeName(String name) {
        if (name == null) return null;
        return name.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String collectionNameForOrg(String organizationName) {
        return "org_" + sanitizeName(organizationName);
    }

    public OrganizationMetadata createOrganization(String orgName, String email, String password) {
        if (orgName == null || orgName.isBlank()) throw new IllegalArgumentException("organization_name required");
        if (adminRepo.findByEmail(email).isPresent()) {
            // avoid duplicate admin email across master admin collection
            throw new DuplicateKeyException("admin email already used");
        }
        if (orgRepo.existsByOrganizationName(orgName)) {
            throw new DuplicateKeyException("organization already exists");
        }

        // create admin user (hash password)
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setOrganizationName(orgName);
        AdminUser savedAdmin = adminRepo.save(admin);

        // create collection dynamically
        String collName = collectionNameForOrg(orgName);
        if (!mongoTemplate.collectionExists(collName)) {
            mongoTemplate.createCollection(collName);
        }

        // create metadata
        OrganizationMetadata meta = new OrganizationMetadata();
        meta.setOrganizationName(orgName);
        meta.setCollectionName(collName);
        meta.setAdminUserId(savedAdmin.getId());
        meta.setConnectionDetails("single_mongo_instance"); // placeholder
        return orgRepo.save(meta);
    }

    public Optional<OrganizationMetadata> getByName(String orgName) {
        return orgRepo.findByOrganizationName(orgName);
    }

    public OrganizationMetadata updateOrganizationName(String currentName, String newName) {
        if (currentName == null || newName == null || newName.isBlank())
            throw new IllegalArgumentException("names required");

        if (!orgRepo.existsByOrganizationName(currentName)) {
            throw new IllegalArgumentException("organization does not exist: " + currentName);
        }
        if (orgRepo.existsByOrganizationName(newName)) {
            throw new IllegalArgumentException("target organization name already exists: " + newName);
        }

        // copy collection data to new collection
        String oldColl = collectionNameForOrg(currentName);
        String newColl = collectionNameForOrg(newName);

        if (!mongoTemplate.collectionExists(newColl)) {
            mongoTemplate.createCollection(newColl);
        }

        // copy all documents from oldColl to newColl
        List<?> docs = mongoTemplate.findAll(Object.class, oldColl);
        if (!docs.isEmpty()) {
            mongoTemplate.insert(docs, newColl);
        }

        // update admin users that reference old org name
        List<AdminUser> admins = adminRepo.findAll();
        admins.stream()
                .filter(a -> currentName.equals(a.getOrganizationName()))
                .forEach(a -> {
                    a.setOrganizationName(newName);
                    adminRepo.save(a);
                });

        // update metadata
        OrganizationMetadata meta = orgRepo.findByOrganizationName(currentName)
                .orElseThrow(() -> new IllegalStateException("metadata not found"));
        meta.setOrganizationName(newName);
        meta.setCollectionName(newColl);
        OrganizationMetadata saved = orgRepo.save(meta);

        // optionally drop old collection (we keep old for safety; but you can drop)
        // mongoTemplate.dropCollection(oldColl);
        return saved;
    }

    public void deleteOrganization(String orgName) {
        OrganizationMetadata meta = orgRepo.findByOrganizationName(orgName)
                .orElseThrow(() -> new IllegalArgumentException("organization not found"));

        // drop collection
        if (mongoTemplate.collectionExists(meta.getCollectionName())) {
            mongoTemplate.dropCollection(meta.getCollectionName());
        }

        // delete admin users that belong to this org
        List<AdminUser> admins = adminRepo.findAll();
        admins.stream()
                .filter(a -> orgName.equals(a.getOrganizationName()))
                .forEach(a -> adminRepo.deleteById(a.getId()));

        // delete metadata
        orgRepo.deleteById(meta.getId());
    }
}
