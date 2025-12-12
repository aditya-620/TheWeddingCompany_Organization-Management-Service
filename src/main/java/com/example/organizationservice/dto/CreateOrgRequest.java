package com.example.organizationservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrgRequest {
    private String organization_name;
    private String email;
    private String password;
}