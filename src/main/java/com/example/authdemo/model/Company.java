package com.example.authdemo.model;

import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Entity
@Table(name = "companies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Company {
    @Column(name = "company_name", nullable = false, unique = true)
    private String companyName;

    @Column(nullable = false, unique = true)
    private String ico;

    @Column(nullable = false)
    private String address;

    @Column
    private String dic;

    @Column(nullable = false)
    private Long admin;

    @Column(nullable = false, unique = true,name = "company_key")
    private String key;


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAdmin() {
        return admin;
    }

    public void setAdmin(Long admin) {
        this.admin = admin;
    }

    public String getDic() {
        return dic;
    }

    public void setDic(String dic) {
        this.dic = dic;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getIco() {
        return ico;
    }

    public void setIco(String ico) {
        this.ico = ico;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    // Aktualizované konstruktory
    public Company(String companyName, String ico, String address, String dic, Long admin, String key) {
        this.companyName = companyName;
        this.ico = ico;
        this.address = address;
        this.dic = dic;
        this.admin = admin;
        this.key = key;
    }

}