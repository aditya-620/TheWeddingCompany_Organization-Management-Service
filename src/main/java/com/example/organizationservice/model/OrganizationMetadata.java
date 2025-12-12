package com.example.organizationservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "master_organizations")
public class OrganizationMetadata {
    @Id
    private String id;
    private String organizationName;
    private String collectionName;
    private String adminUserId; // reference to AdminUser id
    private String connectionDetails;
}
