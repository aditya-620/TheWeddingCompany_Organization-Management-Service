package com.example.organizationservice.controller;

import com.example.organizationservice.dto.LoginRequest;
import com.example.organizationservice.dto.LoginResponse;
import com.example.organizationservice.model.AdminUser;
import com.example.organizationservice.model.OrganizationMetadata;
import com.example.organizationservice.repository.AdminUserRepository;
import com.example.organizationservice.repository.OrganizationMetadataRepository;
import com.example.organizationservice.security.JwtUtil;
import com.example.organizationservice.service.OrganizationService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminController {

    @Autowired
    private AdminUserRepository adminRepo;

    @Autowired
    private OrganizationMetadataRepository orgRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private OrganizationService orgService;

    @PostMapping("/admin/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        AdminUser admin = adminRepo.findByEmail(req.getEmail()).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(401).body("invalid credentials");
        }
        if (!passwordEncoder.matches(req.getPassword(), admin.getPasswordHash())) {
            return ResponseEntity.status(401).body("invalid credentials");
        }
        String token = jwtUtil.generateToken(admin.getId(), admin.getOrganizationName());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @DeleteMapping("/org/delete")
    public ResponseEntity<?> deleteOrg(
            @RequestParam("organization_name") String organization_name,
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body("Authorization header required");
        }
        String token = authorization.substring("Bearer ".length());
        try {
            Jws<Claims> claims = jwtUtil.validateToken(token);
            String orgFromToken = claims.getBody().get("organization", String.class);
            String adminId = claims.getBody().getSubject();
            if (!organization_name.equals(orgFromToken)) {
                return ResponseEntity.status(403).body("token does not belong to this organization");
            }

            // Ensure admin exists and belongs to this org
            AdminUser admin = adminRepo.findById(adminId).orElse(null);
            if (admin == null || !organization_name.equals(admin.getOrganizationName())) {
                return ResponseEntity.status(403).body("invalid admin");
            }

            // perform deletion
            orgService.deleteOrganization(organization_name);
            return ResponseEntity.ok("organization deleted");

        } catch (JwtException ex) {
            return ResponseEntity.status(401).body("invalid token: " + ex.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
