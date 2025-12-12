package com.example.organizationservice.controller;

import com.example.organizationservice.dto.CreateOrgRequest;
import com.example.organizationservice.dto.UpdateOrgRequest;
import com.example.organizationservice.model.OrganizationMetadata;
import com.example.organizationservice.service.OrganizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/org")
public class OrganizationController {

    @Autowired
    private OrganizationService orgService;

    @PostMapping("/create")
    public ResponseEntity<?> createOrganization(@RequestBody CreateOrgRequest req) {
        try {
            OrganizationMetadata meta = orgService.createOrganization(
                    req.getOrganization_name(),
                    req.getEmail(),
                    req.getPassword()
            );
            return ResponseEntity.ok(meta);
        } catch (DuplicateKeyException dke) {
            return ResponseEntity.status(409).body(dke.getMessage());
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }

    @GetMapping("/get")
    public ResponseEntity<?> getOrganization(@RequestParam("organization_name") String orgName) {
        return orgService.getByName(orgName)
                .map(org -> ResponseEntity.ok().body(org))
                .orElseGet(() -> ResponseEntity.status(404).body(new OrganizationMetadata()));
    }


    @PutMapping("/update")
    public ResponseEntity<?> updateOrganization(@RequestBody UpdateOrgRequest req) {
        try {
            OrganizationMetadata updated = orgService.updateOrganizationName(
                    req.getOrganization_name(),
                    req.getNew_organization_name()
            );
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException iae) {
            return ResponseEntity.badRequest().body(iae.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(e.getMessage());
        }
    }
}
