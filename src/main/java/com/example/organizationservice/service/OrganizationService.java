package com.example.organizationservice.service;

import com.example.organizationservice.model.AdminUser;
import com.example.organizationservice.model.OrganizationMetadata;
import com.example.organizationservice.repository.AdminUserRepository;
import com.example.organizationservice.repository.OrganizationMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * OrganizationService
 *
 * - Creates org metadata and admin in master collections.
 * - Dynamically creates tenant collection named org_<sanitized_name>.
 * - Inserts a basic template document into the tenant collection so it is NOT empty.
 * - Inserts a lightweight admin profile document into the tenant collection (no password).
 * - Optionally creates an index on adminEmail inside tenant collection.
 *
 * Note: This service uses MongoTemplate for dynamic collection operations.
 */
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

    /**
     * Create a new organization:
     * - validate inputs
     * - create admin in master_admins (password hashed)
     * - create tenant collection org_<name>
     * - insert a basic template doc into the tenant collection
     * - insert an admin_profile doc into the tenant collection (no password)
     * - save metadata to master_organizations
     */
    public OrganizationMetadata createOrganization(String orgName, String email, String password) {
        if (orgName == null || orgName.isBlank()) throw new IllegalArgumentException("organization_name required");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("admin email required");
        if (password == null || password.isBlank()) throw new IllegalArgumentException("admin password required");

        // avoid duplicate admin email across master admin collection
        if (adminRepo.findByEmail(email).isPresent()) {
            throw new DuplicateKeyException("admin email already used");
        }
        // avoid duplicate organization name
        if (orgRepo.existsByOrganizationName(orgName)) {
            throw new DuplicateKeyException("organization already exists");
        }

        // create admin user (hash password)
        AdminUser admin = new AdminUser();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setOrganizationName(orgName);
        // if AdminUser has role field, you can set it here, e.g. admin.setRole("ADMIN");
        AdminUser savedAdmin = adminRepo.save(admin);

        // create collection dynamically
        String collName = collectionNameForOrg(orgName);
        if (!mongoTemplate.collectionExists(collName)) {
            mongoTemplate.createCollection(collName);

            // --- Insert basic template document (so collection is not empty) ---
            Map<String, Object> basicSchema = new HashMap<>();
            basicSchema.put("template", true);

            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("name", "string");
            fields.put("email", "string");
            fields.put("position", "string");
            fields.put("salary", "number");
            fields.put("createdAt", "date");
            fields.put("updatedAt", "date");

            basicSchema.put("fields", fields);
            basicSchema.put("createdAt", new Date());
            basicSchema.put("description", "This is a template document describing the tenant 'Employee' schema. Remove if needed.");

            mongoTemplate.insert(basicSchema, collName);

            // --- Insert a lightweight admin profile document into tenant collection ---
            Map<String, Object> adminProfile = new HashMap<>();
            adminProfile.put("type", "admin_profile");
            adminProfile.put("adminId", savedAdmin.getId());
            adminProfile.put("adminEmail", savedAdmin.getEmail());
            adminProfile.put("organizationName", orgName);
            adminProfile.put("createdAt", new Date());

            mongoTemplate.insert(adminProfile, collName);

            // --- Create a helpful index on adminEmail inside tenant collection (optional) ---
            try {
                mongoTemplate.indexOps(collName)
                        .ensureIndex(new Index().on("adminEmail", Sort.Direction.ASC));
            } catch (Exception ex) {
                // index creation failure should not block org creation; log if you have logger, otherwise ignore
                // e.g., logger.warn("Could not create index on {}: {}", collName, ex.getMessage());
            }
        }

        // create metadata in master DB
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

    /**
     * Update organization name: create new tenant collection (if needed),
     * copy documents from old collection to new collection, update admin references and metadata.
     */
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
        } else {
            // If old collection was empty, still insert a template into new collection so it's not empty
            Map<String, Object> basicSchema = new HashMap<>();
            basicSchema.put("template", true);
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("name", "string");
            fields.put("email", "string");
            fields.put("position", "string");
            fields.put("salary", "number");
            fields.put("createdAt", "date");
            fields.put("updatedAt", "date");
            basicSchema.put("fields", fields);
            basicSchema.put("createdAt", new Date());
            basicSchema.put("description", "Template added during rename from " + currentName + " to " + newName);
            mongoTemplate.insert(basicSchema, newColl);
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

    /**
     * Update organization details: update admin email and password for the organization.
     */
    public OrganizationMetadata updateOrganization(String orgName, String newEmail, String newPassword) {
        if (orgName == null || orgName.isBlank()) {
            throw new IllegalArgumentException("organization name required");
        }
        if (newEmail == null || newEmail.isBlank()) {
            throw new IllegalArgumentException("email required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("password required");
        }

        OrganizationMetadata meta = orgRepo.findByOrganizationName(orgName)
                .orElseThrow(() -> new IllegalArgumentException("organization not found: " + orgName));

        // Find and update the admin user
        AdminUser admin = adminRepo.findByOrganizationName(orgName)
                .orElseThrow(() -> new IllegalArgumentException("admin not found for organization: " + orgName));

        admin.setEmail(newEmail);
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        adminRepo.save(admin);

        return meta;
    }

    /**
     * Delete an organization:
     * - drop tenant collection
     * - delete admin(s) for that org from master_admins
     * - delete the organization metadata
     */
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
