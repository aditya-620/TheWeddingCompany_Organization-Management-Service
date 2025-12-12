package com.example.organizationservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "master_admins")
public class AdminUser {
    @Id
    private String id;
    private String email;
    private String passwordHash; // bcrypt hash
    private String organizationName; // reference by name
}
