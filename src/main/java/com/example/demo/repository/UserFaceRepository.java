package com.example.demo.repository;


import org.springframework.data.jpa.repository.JpaRepository;

import com.example.demo.model.UserFace;

public interface UserFaceRepository extends JpaRepository<UserFace, Long> {
    
    UserFace findByUsername(String username);
}


