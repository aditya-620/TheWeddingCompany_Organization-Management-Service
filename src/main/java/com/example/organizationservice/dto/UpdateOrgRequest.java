package com.example.organizationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateOrgRequest {
    private String organization_name; // current name
    private String new_organization_name; // new name
    private String email; // admin email (optional)
    private String password; // admin password (optional)
}
