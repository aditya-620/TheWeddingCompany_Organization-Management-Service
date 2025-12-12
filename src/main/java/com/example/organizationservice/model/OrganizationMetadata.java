package com.example.organizationservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "master_organizations")
public class OrganizationMetadata {
    @Id
    private String id;
    private String organizationName;
    private String collectionName;
    private String adminUserId; // reference to AdminUser id
    // optionally connection details field if using multiple DBs
    private String connectionDetails;

    public OrganizationMetadata() {}

    // getters/setters
    public String getId(){ 
        return id; 
    }
    public void setId(String id){ 
        this.id = id; 
    }
    public String getOrganizationName(){ 
        return organizationName; 
    }
    public void setOrganizationName(String organizationName){ 
        this.organizationName = organizationName; 
    }
    public String getCollectionName(){ 
        return collectionName; 
    }
    public void setCollectionName(String collectionName){
         this.collectionName = collectionName; 
    }
    public String getAdminUserId(){
         return adminUserId; 
    }
    public void setAdminUserId(String adminUserId){
         this.adminUserId = adminUserId; 
    }
    public String getConnectionDetails(){
         return connectionDetails; 
    }
    public void setConnectionDetails(String connectionDetails){
         this.connectionDetails = connectionDetails; 
    }
}
